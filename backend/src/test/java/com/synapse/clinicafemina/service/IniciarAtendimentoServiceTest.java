package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.atendimento.IniciarAtendimentoRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.TransferenciaAtendimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IniciarAtendimentoServiceTest {

    @Mock ClinicaRepository clinicaRepository;
    @Mock PacienteRepository pacienteRepository;
    @Mock AtendimentoRepository atendimentoRepository;
    @Mock TransferenciaAtendimentoRepository transferenciaRepository;
    @Mock AtendimentoService atendimentoService;
    @Mock RealtimeBroadcastService broadcastService;

    private IniciarAtendimentoService service;
    private Clinica clinica;
    private Gestor gestor;

    @BeforeEach
    void setUp() {
        service = new IniciarAtendimentoService(
                clinicaRepository, pacienteRepository, atendimentoRepository,
                transferenciaRepository, atendimentoService, broadcastService
        );
        clinica = new Clinica();
        clinica.setId(1L);
        gestor = new Gestor();
        gestor.setId(9L);
        gestor.setClinica(clinica);
        gestor.setPerfil("GESTOR");
        gestor.setAtivo(true);
        gestor.setNome("Gestor Teste");
        lenient().when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));
        lenient().when(pacienteRepository.save(any())).thenAnswer(invocation -> {
            Paciente paciente = invocation.getArgument(0);
            if (paciente.getId() == null) paciente.setId(20L);
            return paciente;
        });
        lenient().when(atendimentoRepository.save(any())).thenAnswer(invocation -> {
            Atendimento atendimento = invocation.getArgument(0);
            if (atendimento.getId() == null) atendimento.setId(30L);
            return atendimento;
        });
        lenient().when(atendimentoService.buscarPorId(30L, 1L)).thenReturn(detalhe());
    }

    @Test
    void should_create_provisional_patient_and_human_attendance_when_phone_is_new() {
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(1L, "5583999999999"))
                .thenReturn(Optional.empty());
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.empty());

        var result = service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(null, "(83) 99999-9999")
        );

        assertTrue(result.pacienteCriado());
        assertTrue(result.atendimentoCriado());
        assertEquals("HUMANO", result.modo());
        verify(pacienteRepository, atLeastOnce()).save(argThat(paciente ->
                paciente.getExternalSource() == ExternalProviderType.WHATSAPP
                        && "5583999999999".equals(paciente.getTelefoneNormalizado())
                        && paciente.getAtendentePrincipal() == gestor
        ));
        verify(atendimentoRepository).save(argThat(atendimento ->
                Boolean.FALSE.equals(atendimento.getTratadoPorIa())
                        && atendimento.getAtendentePrincipal() == gestor
                        && atendimento.getHumanoDesde() != null
        ));
        verifyNoInteractions(transferenciaRepository);
    }

    @Test
    void should_reuse_existing_patient_and_active_attendance_without_overwriting_provider() {
        Paciente paciente = paciente(20L, ExternalProviderType.MEDWARE);
        paciente.setAtendentePrincipal(gestor);
        Atendimento atendimento = atendimento(paciente, gestor, false);
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(1L, "5583999999999"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(atendimento));

        var result = service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(null, "5583999999999")
        );

        assertFalse(result.pacienteCriado());
        assertFalse(result.atendimentoCriado());
        assertTrue(result.atendimentoReutilizado());
        assertFalse(result.destinatarioAlterado());
        assertEquals(ExternalProviderType.MEDWARE, paciente.getExternalSource());
        verifyNoInteractions(transferenciaRepository);
    }

    @Test
    void should_take_over_active_ai_attendance_and_register_manual_transfer() {
        Paciente paciente = paciente(20L, ExternalProviderType.WHATSAPP);
        Atendimento atendimento = atendimento(paciente, null, true);
        when(pacienteRepository.findByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(atendimento));

        var result = service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(20L, null)
        );

        assertFalse(result.atendimentoCriado());
        assertTrue(result.destinatarioAlterado());
        assertFalse(atendimento.getTratadoPorIa());
        assertSame(gestor, atendimento.getAtendentePrincipal());
        verify(transferenciaRepository).save(argThat(transferencia ->
                "MANUAL".equals(transferencia.getOrigem())
                        && transferencia.getParaUsuario() == gestor
                        && transferencia.getTransferidoPor() == gestor
        ));
    }

    @Test
    void should_transfer_active_human_attendance_from_another_attendant_once() {
        Gestor anterior = new Gestor();
        anterior.setId(8L);
        anterior.setClinica(clinica);
        anterior.setPerfil("GESTOR");
        anterior.setAtivo(true);
        Paciente paciente = paciente(20L, ExternalProviderType.DARWIN);
        Atendimento atendimento = atendimento(paciente, anterior, false);
        when(pacienteRepository.findByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(atendimento));

        var result = service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(20L, null)
        );

        assertTrue(result.destinatarioAlterado());
        assertEquals(ExternalProviderType.DARWIN, paciente.getExternalSource());
        assertSame(gestor, atendimento.getAtendentePrincipal());
        verify(transferenciaRepository).save(argThat(transferencia ->
                transferencia.getDeUsuario() == anterior
                        && transferencia.getParaUsuario() == gestor
        ));

        service.iniciar(clinica, gestor, new IniciarAtendimentoRequest(20L, null));
        verify(transferenciaRepository, times(1)).save(any());
    }

    @Test
    void should_keep_same_phone_isolated_by_clinic() {
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(1L, "5583999999999"))
                .thenReturn(Optional.empty());
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.empty());

        service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(null, "5583999999999")
        );

        verify(pacienteRepository).findByClinicaIdAndTelefoneNormalizado(
                1L, "5583999999999"
        );
        verify(pacienteRepository, never()).findByClinicaIdAndTelefoneNormalizado(
                eq(2L), anyString()
        );
    }

    @Test
    void should_reject_inactive_user() {
        gestor.setAtivo(false);

        assertThrows(NotFoundException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(20L, null)
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_patient_from_another_clinic() {
        when(pacienteRepository.findByIdAndClinicaId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(99L, null)
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_soft_deleted_patient_without_creating_duplicate() {
        Paciente paciente = paciente(20L, ExternalProviderType.WHATSAPP);
        paciente.setDeletadoEm(OffsetDateTime.now());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(1L, "5583999999999"))
                .thenReturn(Optional.of(paciente));

        assertThrows(IllegalStateException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(null, "5583999999999")
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_invalid_phone_before_persistence() {
        assertThrows(BadRequestException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(null, "123")
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_user_from_another_clinic() {
        Clinica outra = new Clinica();
        outra.setId(2L);
        gestor.setClinica(outra);

        assertThrows(NotFoundException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(null, "5583999999999")
        ));
        verify(clinicaRepository, never()).findByIdForUpdate(any());
    }

    private Paciente paciente(Long id, ExternalProviderType source) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        paciente.setClinica(clinica);
        paciente.setNome("Paciente Teste");
        paciente.setNomeBusca("PACIENTE TESTE");
        paciente.setTelefone("+5583999999999");
        paciente.setTelefoneNormalizado("5583999999999");
        paciente.setExternalSource(source);
        return paciente;
    }

    private Atendimento atendimento(Paciente paciente, Gestor atendente, boolean ia) {
        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setAtendentePrincipal(atendente);
        atendimento.setTratadoPorIa(ia);
        atendimento.setStatus("ATIVO");
        atendimento.setHumanoDesde(ia ? null : OffsetDateTime.now());
        return atendimento;
    }

    private AtendimentoDetalheDTO detalhe() {
        return new AtendimentoDetalheDTO(
                30L, "ATIVO", false, OffsetDateTime.now(), null, 0,
                new AtendimentoDetalheDTO.PacienteDetalheDTO(
                        20L, "Paciente Teste", "5583999999999", null,
                        "EM_ATENDIMENTO", null, null, false,
                        null, null, null, null
                ),
                new AtendimentoDetalheDTO.AtendenteDTO(9L, "Gestor Teste", "GESTOR")
        );
    }
}
