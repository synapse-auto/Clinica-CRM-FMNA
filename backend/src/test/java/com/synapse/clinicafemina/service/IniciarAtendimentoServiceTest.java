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
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.TransferenciaAtendimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
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
    @Mock MensagemRepository mensagemRepository;
    private WhatsappPhoneIdentityService phoneIdentityService;
    private final WhatsappContactNameService contactNameService = new WhatsappContactNameService();

    private IniciarAtendimentoService service;
    private Clinica clinica;
    private Gestor gestor;

    @BeforeEach
    void setUp() {
        phoneIdentityService = new WhatsappPhoneIdentityService(
                pacienteRepository, atendimentoRepository, mensagemRepository
        );
        service = new IniciarAtendimentoService(
                clinicaRepository, pacienteRepository, atendimentoRepository,
                transferenciaRepository, atendimentoService, broadcastService,
                phoneIdentityService, contactNameService
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
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of());
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.empty());

        var result = service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(
                        null, "(83) 99999-9999", "Contato Teste"
                )
        );

        assertTrue(result.pacienteCriado());
        assertTrue(result.atendimentoCriado());
        assertEquals("HUMANO", result.modo());
        verify(pacienteRepository, atLeastOnce()).save(argThat(paciente ->
                paciente.getExternalSource() == ExternalProviderType.WHATSAPP
                        && "5583999999999".equals(paciente.getTelefoneNormalizado())
                        && "Contato Teste".equals(paciente.getNome())
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
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(paciente));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(atendimento));

        var result = service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(
                        null, "5583999999999", "Contato Teste"
                )
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
                clinica, gestor, new IniciarAtendimentoRequest(20L, null, null)
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
                clinica, gestor, new IniciarAtendimentoRequest(20L, null, null)
        );

        assertTrue(result.destinatarioAlterado());
        assertEquals(ExternalProviderType.DARWIN, paciente.getExternalSource());
        assertSame(gestor, atendimento.getAtendentePrincipal());
        verify(transferenciaRepository).save(argThat(transferencia ->
                transferencia.getDeUsuario() == anterior
                        && transferencia.getParaUsuario() == gestor
        ));

        service.iniciar(clinica, gestor, new IniciarAtendimentoRequest(20L, null, null));
        verify(transferenciaRepository, times(1)).save(any());
    }

    @Test
    void should_keep_same_phone_isolated_by_clinic() {
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of());
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.empty());

        service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(
                        null, "5583999999999", "Contato Teste"
                )
        );

        verify(pacienteRepository).findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any());
        verify(pacienteRepository, never()).findByClinicaIdAndTelefoneNormalizadoIn(
                eq(2L), any()
        );
    }

    @Test
    void should_reject_inactive_user() {
        gestor.setAtivo(false);

        assertThrows(NotFoundException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(20L, null, null)
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_patient_from_another_clinic() {
        when(pacienteRepository.findByIdAndClinicaId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(99L, null, null)
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_soft_deleted_patient_without_creating_duplicate() {
        Paciente paciente = paciente(20L, ExternalProviderType.WHATSAPP);
        paciente.setDeletadoEm(OffsetDateTime.now());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(paciente));
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(
                1L, "5583999999999"
        )).thenReturn(Optional.of(paciente));

        assertThrows(IllegalStateException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(
                        null, "5583999999999", "Contato Teste"
                )
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_invalid_phone_before_persistence() {
        assertThrows(BadRequestException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(null, "123", "Contato Teste")
        ));
        verifyNoInteractions(atendimentoRepository);
    }

    @Test
    void should_reject_user_from_another_clinic() {
        Clinica outra = new Clinica();
        outra.setId(2L);
        gestor.setClinica(outra);

        assertThrows(NotFoundException.class, () -> service.iniciar(
                clinica, gestor, new IniciarAtendimentoRequest(
                        null, "5583999999999", "Contato Teste"
                )
        ));
        verify(clinicaRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void should_reuse_legacy_mobile_alias_and_existing_active_attendance() {
        Paciente patient = paciente(20L, ExternalProviderType.WHATSAPP);
        patient.setTelefone("+558391114004");
        patient.setTelefoneNormalizado("558391114004");
        Atendimento active = atendimento(patient, gestor, false);
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(patient));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(active));
        when(mensagemRepository.countByAtendimentoAndDirecao(1L, 30L, "ENTRADA"))
                .thenReturn(1L);

        var result = service.iniciar(
                clinica,
                gestor,
                new IniciarAtendimentoRequest(
                        null, "5583991114004", "Marcondss Teste"
                )
        );

        assertEquals(20L, result.pacienteId());
        assertEquals(30L, result.atendimentoId());
        assertFalse(result.pacienteCriado());
        assertTrue(result.atendimentoReutilizado());
        assertEquals("558391114004", active.getWhatsappChatId());
        verify(pacienteRepository, never()).save(argThat(item ->
                item.getId() == null || item.getId() != 20L));
    }

    @Test
    void should_reuse_modern_mobile_alias_when_legacy_phone_is_supplied() {
        Paciente patient = paciente(20L, ExternalProviderType.WHATSAPP);
        patient.setTelefone("+5583991114004");
        patient.setTelefoneNormalizado("5583991114004");
        Atendimento active = atendimento(patient, gestor, false);
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(patient));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(active));

        var result = service.iniciar(
                clinica,
                gestor,
                new IniciarAtendimentoRequest(null, "558391114004", "Marcondss Teste")
        );

        assertEquals(20L, result.pacienteId());
        assertEquals(30L, result.atendimentoId());
        assertFalse(result.pacienteCriado());
    }

    @Test
    void should_update_whatsapp_placeholder_but_preserve_external_patient_name() {
        Paciente whatsapp = paciente(20L, ExternalProviderType.WHATSAPP);
        whatsapp.setNome(WhatsappContactNameService.PLACEHOLDER);
        whatsapp.setNomeBusca("CONTATO WHATSAPP");
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(whatsapp));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.empty());

        service.iniciar(
                clinica,
                gestor,
                new IniciarAtendimentoRequest(null, "5583999999999", "Maria Ávila")
        );

        assertEquals("Maria Ávila", whatsapp.getNome());
        assertEquals("MARIA ÁVILA", whatsapp.getNomeBusca());

        Paciente medware = paciente(20L, ExternalProviderType.MEDWARE);
        medware.setNome(WhatsappContactNameService.PLACEHOLDER);
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(medware));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(
                atendimento(medware, gestor, false)
        ));

        service.iniciar(
                clinica,
                gestor,
                new IniciarAtendimentoRequest(null, "5583999999999", "Nome Manual")
        );

        assertEquals(WhatsappContactNameService.PLACEHOLDER, medware.getNome());

        Paciente darwin = paciente(20L, ExternalProviderType.DARWIN);
        darwin.setNome(WhatsappContactNameService.PLACEHOLDER);
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(darwin));
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(
                atendimento(darwin, gestor, false)
        ));

        service.iniciar(
                clinica,
                gestor,
                new IniciarAtendimentoRequest(null, "5583999999999", "Nome Manual")
        );

        assertEquals(WhatsappContactNameService.PLACEHOLDER, darwin.getNome());
    }

    @Test
    void should_reject_real_identity_conflict_without_creating_third_patient() {
        Paciente legacy = paciente(20L, ExternalProviderType.WHATSAPP);
        legacy.setTelefoneNormalizado("558391114004");
        legacy.setNome("Paciente Legado");
        Paciente modern = paciente(21L, ExternalProviderType.WHATSAPP);
        modern.setTelefoneNormalizado("5583991114004");
        modern.setNome("Paciente Moderno");
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(legacy, modern));
        when(atendimentoRepository.findHistoricoPaciente(eq(1L), anyLong()))
                .thenReturn(List.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.iniciar(
                        clinica,
                        gestor,
                        new IniciarAtendimentoRequest(
                                null, "5583991114004", "Outro Contato"
                        )
                )
        );

        assertEquals(WhatsappPhoneIdentityService.CONFLICT_MESSAGE, error.getMessage());
        verify(pacienteRepository, never()).save(argThat(item -> item.getId() == null));
        verify(atendimentoRepository, never()).save(any());
    }

    @Test
    void should_prefer_established_patient_over_exact_provisional_duplicate() {
        Paciente established = paciente(20L, ExternalProviderType.WHATSAPP);
        established.setTelefoneNormalizado("558391114004");
        established.setNome("Marcondss");
        Paciente provisional = paciente(21L, ExternalProviderType.WHATSAPP);
        provisional.setTelefoneNormalizado("5583991114004");
        provisional.setNome(WhatsappContactNameService.PLACEHOLDER);
        provisional.setNomeBusca("CONTATO WHATSAPP");
        Atendimento active = atendimento(established, gestor, false);
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), any()))
                .thenReturn(List.of(established, provisional));
        when(atendimentoRepository.findHistoricoPaciente(eq(1L), anyLong()))
                .thenReturn(List.of());
        when(atendimentoRepository.findAtivo(1L, 20L)).thenReturn(Optional.of(active));

        var result = service.iniciar(
                clinica,
                gestor,
                new IniciarAtendimentoRequest(
                        null, "5583991114004", "Marcondss Teste"
                )
        );

        assertEquals(20L, result.pacienteId());
        assertEquals(30L, result.atendimentoId());
        assertTrue(result.atendimentoReutilizado());
        assertEquals(WhatsappContactNameService.PLACEHOLDER, provisional.getNome());
        verify(pacienteRepository, never()).delete(any());
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
