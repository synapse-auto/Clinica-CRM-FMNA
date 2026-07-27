package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.PacienteFotoPerfil;
import com.synapse.clinicafemina.domain.PacienteFotoStatus;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapProfilePhotoImageValidator;
import com.synapse.clinicafemina.repository.PacienteFotoPerfilRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteFotoPerfilServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);

    @Mock private PacienteRepository pacienteRepository;
    @Mock private PacienteFotoPerfilRepository fotoRepository;

    private PacienteFotoPerfilService service;
    private Paciente paciente;
    private PacienteFotoPerfil estado;

    @BeforeEach
    void setUp() {
        service = new PacienteFotoPerfilService(pacienteRepository, fotoRepository, CLOCK);
        Clinica clinica = new Clinica();
        clinica.setId(2L);
        paciente = new Paciente();
        paciente.setId(10L);
        paciente.setClinica(clinica);
        paciente.setTelefoneNormalizado("5511999990000");

        estado = new PacienteFotoPerfil();
        estado.setPacienteId(10L);
        estado.setPaciente(paciente);
        estado.setClinica(clinica);
        estado.setProvider(WhatsappProviderType.UAZAP);
        estado.setStatus(PacienteFotoStatus.PENDING);
        estado.setTentativas(1);
    }

    @Test
    void startsAtomicClaimInsideTheSameClinic() {
        when(pacienteRepository.findForPhotoUpdateByIdAndClinicaId(10L, 2L))
                .thenReturn(Optional.of(paciente));
        when(fotoRepository.findByPacienteIdAndClinica_Id(10L, 2L))
                .thenReturn(Optional.of(estado));

        Optional<PacienteFotoPerfilService.TentativaFoto> result =
                service.iniciar(10L, 2L, false);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().telefoneNormalizado()).isEqualTo("5511999990000");
        verify(fotoRepository).save(estado);
        assertThat(estado.getStatus()).isEqualTo(PacienteFotoStatus.PENDING);
        assertThat(estado.getTentativas()).isEqualTo(2);
    }

    @Test
    void wrongClinicNeverCreatesOrClaimsState() {
        when(pacienteRepository.findForPhotoUpdateByIdAndClinicaId(10L, 3L))
                .thenReturn(Optional.empty());

        assertThat(service.iniciar(10L, 3L, false)).isEmpty();
        verify(fotoRepository, never()).save(any());
    }

    @Test
    void concurrentOrCooldownClaimIsIgnored() {
        estado.setUltimaTentativaEm(java.time.OffsetDateTime.now(CLOCK));
        when(pacienteRepository.findForPhotoUpdateByIdAndClinicaId(10L, 2L))
                .thenReturn(Optional.of(paciente));
        when(fotoRepository.findByPacienteIdAndClinica_Id(10L, 2L))
                .thenReturn(Optional.of(estado));

        assertThat(service.iniciar(10L, 2L, false)).isEmpty();
    }

    @Test
    void successStoresBytesAndOnlyAnInternalVersionedUrl() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        when(pacienteRepository.findByIdAndClinicaId(10L, 2L)).thenReturn(Optional.of(paciente));
        when(fotoRepository.findByPacienteIdAndClinica_Id(10L, 2L))
                .thenReturn(Optional.of(estado));

        String url = service.salvarSucesso(
                new PacienteFotoPerfilService.TentativaFoto(10L, 2L, "5511999990000", 1),
                new UazapProfilePhotoImageValidator.ValidatedImage(png, "image/png")
        );

        assertThat(url).matches("/api/pacientes/10/foto\\?v=[a-f0-9]{12}");
        assertThat(paciente.getFotoUrl()).isEqualTo(url);
        assertThat(estado.getConteudo()).containsExactly(png);
        assertThat(estado.getContentType()).isEqualTo("image/png");
        assertThat(estado.getSha256()).hasSize(64);
        assertThat(estado.getStatus()).isEqualTo(PacienteFotoStatus.SUCCESS);
        verify(pacienteRepository).save(paciente);
        verify(fotoRepository).save(estado);
    }

    @Test
    void failureKeepsPreviouslyStoredImageAndPatientUrl() {
        byte[] existing = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        paciente.setFotoUrl("/api/pacientes/10/foto?v=abc12345");
        estado.setConteudo(existing.clone());
        estado.setContentType("image/jpeg");
        estado.setSha256("a".repeat(64));
        estado.setTamanhoBytes((long) existing.length);
        when(fotoRepository.findByPacienteIdAndClinica_Id(10L, 2L))
                .thenReturn(Optional.of(estado));

        service.registrarFalha(
                new PacienteFotoPerfilService.TentativaFoto(10L, 2L, "5511999990000", 2),
                "HTTP 500",
                true
        );

        assertThat(estado.getConteudo()).containsExactly(existing);
        assertThat(paciente.getFotoUrl()).contains("/api/pacientes/10/foto");
        assertThat(estado.getStatus()).isEqualTo(PacienteFotoStatus.TEMPORARY_FAILURE);
        assertThat(estado.getMotivoUltimaFalha()).isEqualTo("HTTP_500");
    }

    @Test
    void previouslyStoredPhotoRemainsReadableDuringBackoff() {
        byte[] existing = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        estado.setConteudo(existing.clone());
        estado.setContentType("image/jpeg");
        estado.setSha256("a".repeat(64));
        estado.setTamanhoBytes((long) existing.length);
        estado.setStatus(PacienteFotoStatus.TEMPORARY_FAILURE);
        when(pacienteRepository.findByIdAndClinicaId(10L, 2L)).thenReturn(Optional.of(paciente));
        when(fotoRepository.findByPacienteIdAndClinica_Id(10L, 2L))
                .thenReturn(Optional.of(estado));

        PacienteFotoPerfilService.FotoArmazenada result = service.obter(10L, 2L);

        assertThat(result.conteudo()).containsExactly(existing);
        assertThat(result.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void photoReadDoesNotRevealCrossClinicExistence() {
        when(pacienteRepository.findByIdAndClinicaId(10L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obter(10L, 3L))
                .hasMessage("Foto nao encontrada");
        verify(fotoRepository, never()).findByPacienteIdAndClinica_Id(any(), any());
    }
}
