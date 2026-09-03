package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoTagRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteTagRepository;
import com.synapse.clinicafemina.repository.TransferenciaAtendimentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtendimentoServiceN8nClosureTest {

    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private ClinicaRepository clinicaRepository;
    @Mock private MensagemRepository mensagemRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TransferenciaAtendimentoRepository transferenciaRepository;
    @Mock private AtendimentoNotificationService notificationService;
    @Mock private RealtimeBroadcastService broadcastService;
    @Mock private AtendimentoTagRepository atendimentoTagRepository;
    @Mock private PacienteTagRepository pacienteTagRepository;
    @Mock private WhatsappWindowService whatsappWindowService;
    @Mock private WhatsappOutboundClient whatsappOutboundClient;

    private AtendimentoService service;
    private Atendimento atendimento;

    @BeforeEach
    void setUp() {
        service = new AtendimentoService(
                atendimentoRepository,
                clinicaRepository,
                mensagemRepository,
                usuarioRepository,
                transferenciaRepository,
                notificationService,
                broadcastService,
                atendimentoTagRepository,
                pacienteTagRepository,
                whatsappWindowService,
                whatsappOutboundClient
        );
        when(whatsappWindowService.avaliar(30L, 7L))
                .thenReturn(new WhatsappWindowService.WindowState(false, null, null, false));
        atendimento = atendimento();
    }

    @Test
    void should_close_attendance_from_n8n_and_preserve_history() {
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(30L, 7L))
                .thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.save(atendimento)).thenReturn(atendimento);

        AtendimentoDetalheDTO result = service.encerrarPorN8n(30L, 7L, "Encerrado pelo N8N");

        assertEquals("ENCERRADO", result.status());
        assertEquals("Encerrado pelo N8N", atendimento.getMotivoEncerramento());
        verify(atendimentoRepository).save(atendimento);
    }

    @Test
    void should_make_repeated_n8n_closure_idempotent() {
        OffsetDateTime encerradoEm = OffsetDateTime.parse("2026-09-03T12:00:00Z");
        atendimento.setStatus("ENCERRADO");
        atendimento.setDataEncerramento(encerradoEm);
        atendimento.setMotivoEncerramento("Motivo original");
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(30L, 7L))
                .thenReturn(Optional.of(atendimento));

        AtendimentoDetalheDTO result = service.encerrarPorN8n(30L, 7L, "Outro motivo");

        assertEquals(encerradoEm, result.dataEncerramento());
        assertEquals("Motivo original", atendimento.getMotivoEncerramento());
        verify(atendimentoRepository, never()).save(any());
    }

    private Atendimento atendimento() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNome("Paciente");
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefone("5544999999999");
        paciente.setTelefoneNormalizado("5544999999999");
        paciente.setRequerRevisao(false);

        Atendimento value = new Atendimento();
        value.setId(30L);
        value.setClinica(clinica);
        value.setPaciente(paciente);
        value.setStatus("ATIVO");
        value.setTratadoPorIa(false);
        value.setNaoLidas(0);
        value.setDataInicio(OffsetDateTime.parse("2026-09-03T11:00:00Z"));
        return value;
    }
}
