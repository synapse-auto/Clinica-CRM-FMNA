package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UazapProfilePhotoEnrichmentService — regra central de enriquecimento (provider, sobrescrita, falhas)")
class UazapProfilePhotoEnrichmentServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private UazapProfilePhotoClient photoClient;

    @Mock
    private UazapPicturePayloadParser payloadParser;

    private WhatsappProperties whatsappProperties;
    private UazapProfilePhotoEnrichmentService service;

    @BeforeEach
    void setUp() {
        whatsappProperties = new WhatsappProperties();
        service = new UazapProfilePhotoEnrichmentService(pacienteRepository, whatsappProperties, photoClient, payloadParser);
    }

    private Paciente pacienteSemFoto() {
        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setTelefoneNormalizado("5511999990000");
        return paciente;
    }

    @Test
    @DisplayName("provider META (default): não consulta a UAZAP, nunca chama o cliente")
    void metaProvider_neverCallsUazapClient() {
        whatsappProperties.setProvider("META");

        UazapPictureEnrichmentOutcome outcome = service.enriquecer(1L);

        assertThat(outcome.motivoNaoPersistida()).isEqualTo("PROVIDER_ATIVO_NAO_E_UAZAP");
        verify(photoClient, never()).buscarFotoPerfil(anyString());
        verify(pacienteRepository, never()).findById(any());
    }

    @Test
    @DisplayName("provider UAZAP + paciente sem foto + telefone disponível: consulta e persiste a foto retornada")
    void uazapProvider_noExistingPhoto_enrichesSuccessfully() {
        whatsappProperties.setProvider("UAZAP");
        Paciente paciente = pacienteSemFoto();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        UazapPictureRawResponse raw = new UazapPictureRawResponse(200, "application/json", "{}".getBytes());
        when(photoClient.buscarFotoPerfil("5511999990000")).thenReturn(raw);
        when(payloadParser.parse(raw)).thenReturn(new UazapPictureEnrichmentOutcome(
                200, "application/json", 2, "JSON", java.util.List.of("url"),
                true, false, false, "https://cdn.example/foto.jpg", false, null, java.util.List.of("url:string")));

        UazapPictureEnrichmentOutcome outcome = service.enriquecer(1L);

        assertThat(outcome.fotoPersistida()).isTrue();
        assertThat(paciente.getFotoUrl()).isEqualTo("https://cdn.example/foto.jpg");
        verify(pacienteRepository).save(paciente);
    }

    @Test
    @DisplayName("paciente já possui foto: nunca sobrescreve, nunca chama a UAZAP")
    void existingPhoto_isNeverOverwritten() {
        whatsappProperties.setProvider("UAZAP");
        Paciente paciente = pacienteSemFoto();
        paciente.setFotoUrl("https://cdn.example/ja-existente.jpg");
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        UazapPictureEnrichmentOutcome outcome = service.enriquecer(1L);

        assertThat(outcome.motivoNaoPersistida()).isEqualTo("PACIENTE_JA_POSSUI_FOTO");
        assertThat(paciente.getFotoUrl()).isEqualTo("https://cdn.example/ja-existente.jpg");
        verify(photoClient, never()).buscarFotoPerfil(anyString());
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("telefone indisponível: não chama a UAZAP")
    void missingPhone_doesNotCallUazap() {
        whatsappProperties.setProvider("UAZAP");
        Paciente paciente = pacienteSemFoto();
        paciente.setTelefoneNormalizado(null);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));

        UazapPictureEnrichmentOutcome outcome = service.enriquecer(1L);

        assertThat(outcome.motivoNaoPersistida()).isEqualTo("TELEFONE_INDISPONIVEL");
        verify(photoClient, never()).buscarFotoPerfil(anyString());
    }

    @Test
    @DisplayName("falha do cliente UAZAP nunca propaga exceção; paciente permanece sem foto")
    void clientFailure_neverPropagatesAndDoesNotPersist() {
        whatsappProperties.setProvider("UAZAP");
        Paciente paciente = pacienteSemFoto();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(photoClient.buscarFotoPerfil(anyString()))
                .thenThrow(new com.synapse.clinicafemina.integration.whatsapp.uazap.exception.UazapException("timeout simulado"));

        assertThatCode(() -> service.enriquecer(1L)).doesNotThrowAnyException();
        UazapPictureEnrichmentOutcome outcome = service.enriquecer(1L);
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("FALHA_DE_COMUNICACAO_COM_UAZAP");
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("parser não encontra foto válida: paciente permanece sem foto, sem erro")
    void parserReturnsNoPhoto_doesNotPersist() {
        whatsappProperties.setProvider("UAZAP");
        Paciente paciente = pacienteSemFoto();
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        UazapPictureRawResponse raw = new UazapPictureRawResponse(200, "application/json", "{}".getBytes());
        when(photoClient.buscarFotoPerfil("5511999990000")).thenReturn(raw);
        when(payloadParser.parse(raw)).thenReturn(UazapPictureEnrichmentOutcome.semTentativa("NENHUM_CAMPO_DE_FOTO_RECONHECIDO"));

        UazapPictureEnrichmentOutcome outcome = service.enriquecer(1L);

        assertThat(outcome.fotoPersistida()).isFalse();
        assertThat(paciente.getFotoUrl()).isNull();
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("paciente inexistente: retorna diagnóstico sem lançar exceção")
    void unknownPaciente_returnsDiagnosticWithoutThrowing() {
        whatsappProperties.setProvider("UAZAP");
        when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

        UazapPictureEnrichmentOutcome outcome = service.enriquecer(99L);

        assertThat(outcome.motivoNaoPersistida()).isEqualTo("PACIENTE_NAO_ENCONTRADO");
        verify(photoClient, never()).buscarFotoPerfil(anyString());
    }
}
