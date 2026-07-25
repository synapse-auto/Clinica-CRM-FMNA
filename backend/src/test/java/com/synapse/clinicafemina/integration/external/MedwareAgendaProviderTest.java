package com.synapse.clinicafemina.integration.external;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.exception.AgendaOperationNotSupportedException;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AgendaExternalDoctorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedwareAgendaProvider — leitura provider-agnostic sem alterar comportamento Medware")
class MedwareAgendaProviderTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private AgendaExternalDoctorResolver externalDoctorResolver;

    private MedwareAgendaProvider provider;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        provider = new MedwareAgendaProvider(agendamentoRepository, pacienteRepository, externalDoctorResolver);
        clinica = new Clinica();
        clinica.setId(1L);
        lenient().when(externalDoctorResolver.resolve(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("capacidades: bulkSync=false (n/a aqui), clinicWide=true, fitIn=false, write=false")
    void capabilities_matchMedwareLimitations() {
        assertThat(provider.providerType()).isEqualTo(ExternalProviderType.MEDWARE);
        assertThat(provider.supportsClinicWideListing()).isTrue();
        assertThat(provider.supportsFitIn()).isFalse();
        assertThat(provider.supportsWriteOperations()).isFalse();
    }

    @Test
    @DisplayName("capacidades explicitas: sem catalogo, sem busca de paciente, sem backfill, coverage=FULL")
    void capabilities_explicit_matchMedwareLimitations() {
        assertThat(provider.supportsCatalog()).isFalse();
        assertThat(provider.supportsPatientLookup()).isFalse();
        assertThat(provider.supportsBackfill()).isFalse();
        assertThat(provider.coverage()).isEqualTo("FULL");
    }

    @Test
    @DisplayName("listarAgenda mapeia Agendamento existente para o DTO normalizado sem alterar dados")
    void listarAgenda_mapsExistingAgendamento() {
        Paciente paciente = new Paciente();
        paciente.setId(10L);
        paciente.setNome("Paciente Teste");
        paciente.setCpf("11144477735");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(5L);
        agendamento.setExternalSource(ExternalProviderType.MEDWARE);
        agendamento.setExternalId("ext-1");
        agendamento.setPaciente(paciente);
        agendamento.setDataHoraInicio(OffsetDateTime.parse("2026-07-20T09:00:00-03:00"));
        agendamento.setDataHoraFim(OffsetDateTime.parse("2026-07-20T09:30:00-03:00"));
        agendamento.setStatus("AGENDADO");
        agendamento.setServicoNome("Consulta");
        agendamento.setOrigem("INTEGRACAO_EXTERNA");

        OffsetDateTime inicio = OffsetDateTime.parse("2026-07-20T00:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-07-21T00:00:00-03:00");
        when(agendamentoRepository
                .findByClinicaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                        1L, inicio, fim))
                .thenReturn(List.of(agendamento));

        List<AgendaAgendamentoDTO> resultado = provider.listarAgenda(clinica, inicio, fim);

        assertThat(resultado).hasSize(1);
        AgendaAgendamentoDTO dto = resultado.get(0);
        assertThat(dto.idLocal()).isEqualTo(5L);
        assertThat(dto.pacienteNome()).isEqualTo("Paciente Teste");
        assertThat(dto.pacienteCpfMascarado()).isEqualTo("***.***.777-35");
        assertThat(dto.status()).isEqualTo("AGENDADO");
    }

    @Test
    @DisplayName("listarPorPaciente com CPF sem paciente correspondente retorna lista vazia")
    void listarPorPaciente_returnsEmptyWhenPatientNotFound() {
        when(pacienteRepository.findByClinicaIdAndCpfHash(any(), any())).thenReturn(Optional.empty());

        List<AgendaAgendamentoDTO> resultado = provider.listarPorPaciente(clinica, "11144477735");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("operacoes de escrita e catalogo sob demanda lancam AgendaOperationNotSupportedException")
    void unsupportedOperations_throwExplicitException() {
        assertThatThrownBy(() -> provider.listarProfissionais(clinica))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.listarHorariosDisponiveis(clinica, null, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.criarAgendamento(clinica, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.cancelarAgendamento(clinica, 1L, "motivo"))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
    }

    @Test
    @DisplayName("regressao UltraMedical: NENHUMA operacao de catalogo/escrita/Darwin e inventada — "
            + "todas as 9 operacoes fora das 3 de leitura lancam AgendaOperationNotSupportedException")
    void unsupportedOperations_exhaustive_neverInventsWritesOrCatalogCalls() {
        assertThatThrownBy(() -> provider.listarProfissionais(clinica))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.listarHorariosDisponiveis(clinica, null, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.listarProcedimentos(clinica, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.listarConvenios(clinica, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.listarLocais(clinica))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.buscarPaciente(clinica, "11144477735"))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.criarOuLocalizarPaciente(clinica, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.criarAgendamento(clinica, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.criarEncaixe(clinica, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.atualizarAgendamento(clinica, 1L, null))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
        assertThatThrownBy(() -> provider.cancelarAgendamento(clinica, 1L, "motivo"))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
    }
}
