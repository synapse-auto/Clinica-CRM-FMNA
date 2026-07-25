package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.AgendaIdempotencyKey;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.IdempotencyConflictException;
import com.synapse.clinicafemina.repository.AgendaIdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Idempotência explícita por chave (nunca por conteúdo). O estado vive inteiramente no
 * repositório (mockado aqui como se fosse a tabela agenda_idempotency_key) — o service
 * não guarda nenhum cache em memória, então dois "restarts" (duas instâncias novas do
 * service sobre o mesmo repositório) produzem exatamente o mesmo comportamento.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("N8nIdempotencyService — chave explícita, isolamento por clínica+operação")
class N8nIdempotencyServiceTest {

    @Mock
    private AgendaIdempotencyKeyRepository repository;

    @Mock
    private AgendaService agendaService;

    private N8nIdempotencyService service;
    private Clinica clinicaA;
    private Clinica clinicaB;
    private NovoAgendamentoRequest payload;
    private NovoAgendamentoRequest payloadDiferente;

    @BeforeEach
    void setUp() {
        service = new N8nIdempotencyService(repository, agendaService);
        clinicaA = new Clinica();
        clinicaA.setId(1L);
        clinicaB = new Clinica();
        clinicaB.setId(2L);
        payload = new NovoAgendamentoRequest(
                1L, null, "prof-1", "loc-1", "tt-1",
                LocalDate.parse("2026-07-20"), "09:00", "09:30", null, "Consulta", null, null);
        payloadDiferente = new NovoAgendamentoRequest(
                1L, null, "prof-2", "loc-1", "tt-2",
                LocalDate.parse("2026-07-21"), "11:00", "11:30", null, "Consulta", null, null);
    }

    @Test
    void should_require_idempotency_key() {
        assertThatThrownBy(() -> service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, null, payload,
                agendaService::criarAgendamento))
                .isInstanceOf(BadRequestException.class);

        verify(agendaService, never()).criarAgendamento(any(), any());
    }

    @Test
    @DisplayName("retry identico com a mesma chave retorna o resultado original sem criar de novo")
    void should_return_original_result_on_identical_retry() {
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(registro(clinicaA, "key-1", payload, 55L)));
        when(agendaService.criarAgendamento(clinicaA, payload)).thenReturn(agendamento(55L));
        when(agendaService.buscarPorId(clinicaA, 55L)).thenReturn(agendamento(55L));

        AgendaAgendamentoDTO primeira = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-1", payload,
                agendaService::criarAgendamento);
        AgendaAgendamentoDTO segunda = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-1", payload,
                agendaService::criarAgendamento);

        assertThat(primeira.idLocal()).isEqualTo(55L);
        assertThat(segunda.idLocal()).isEqualTo(55L);
        verify(agendaService, times(1)).criarAgendamento(clinicaA, payload);
        verify(agendaService, times(1)).buscarPorId(clinicaA, 55L);
    }

    @Test
    @DisplayName("mesma chave com payload diferente retorna 409 (IdempotencyConflictException)")
    void should_reject_same_key_with_different_payload() {
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "key-2"))
                .thenReturn(Optional.of(registro(clinicaA, "key-2", payload, 55L)));

        assertThatThrownBy(() -> service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-2", payloadDiferente,
                agendaService::criarAgendamento))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(agendaService, never()).criarAgendamento(any(), any());
    }

    @Test
    @DisplayName("chave diferente sempre permite nova operacao, mesmo com payload identico")
    void should_allow_new_operation_for_different_key() {
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "key-3"))
                .thenReturn(Optional.empty());
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "key-4"))
                .thenReturn(Optional.empty());
        when(agendaService.criarAgendamento(clinicaA, payload))
                .thenReturn(agendamento(10L))
                .thenReturn(agendamento(11L));

        AgendaAgendamentoDTO primeiro = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-3", payload,
                agendaService::criarAgendamento);
        AgendaAgendamentoDTO segundo = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-4", payload,
                agendaService::criarAgendamento);

        assertThat(primeiro.idLocal()).isNotEqualTo(segundo.idLocal());
        verify(agendaService, times(2)).criarAgendamento(clinicaA, payload);
    }

    @Test
    @DisplayName("duas clinicas com a mesma idempotency key nao colidem entre si")
    void should_isolate_idempotency_key_by_clinic() {
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "key-mesma"))
                .thenReturn(Optional.empty());
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(2L, "CRIAR_AGENDAMENTO", "key-mesma"))
                .thenReturn(Optional.empty());
        when(agendaService.criarAgendamento(clinicaA, payload)).thenReturn(agendamento(1L));
        when(agendaService.criarAgendamento(clinicaB, payload)).thenReturn(agendamento(2L));

        AgendaAgendamentoDTO resultadoA = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-mesma", payload,
                agendaService::criarAgendamento);
        AgendaAgendamentoDTO resultadoB = service.executarCriacaoIdempotente(
                clinicaB, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-mesma", payload,
                agendaService::criarAgendamento);

        assertThat(resultadoA.idLocal()).isEqualTo(1L);
        assertThat(resultadoB.idLocal()).isEqualTo(2L);
        verify(repository, never())
                .findByClinicaIdAndOperacaoAndIdempotencyKey(eq(1L), any(), eq("key-mesma-outra-clinica"));
    }

    @Test
    @DisplayName("mesma chave em operacoes diferentes (normal x encaixe) nao colide")
    void should_not_collide_between_normal_and_fitin_operations_at_same_key() {
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "mesmo-horario"))
                .thenReturn(Optional.empty());
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_ENCAIXE", "mesmo-horario"))
                .thenReturn(Optional.empty());
        when(agendaService.criarAgendamento(clinicaA, payload)).thenReturn(agendamento(1L));
        when(agendaService.criarEncaixe(clinicaA, payload)).thenReturn(agendamento(2L));

        AgendaAgendamentoDTO normal = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "mesmo-horario", payload,
                agendaService::criarAgendamento);
        AgendaAgendamentoDTO encaixe = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_ENCAIXE, "mesmo-horario", payload,
                agendaService::criarEncaixe);

        assertThat(normal.idLocal()).isEqualTo(1L);
        assertThat(encaixe.idLocal()).isEqualTo(2L);
    }

    @Test
    @DisplayName("corrida de insert concorrente (restricao unica) retorna o resultado do vencedor, sem duplicar")
    void should_resolve_concurrent_insert_race_without_duplicating() {
        AtomicInteger chamada = new AtomicInteger(0);
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "key-concorrente"))
                .thenAnswer(invocation -> chamada.getAndIncrement() == 0
                        ? Optional.empty()
                        : Optional.of(registro(clinicaA, "key-concorrente", payload, 99L)));
        when(agendaService.criarAgendamento(clinicaA, payload)).thenReturn(agendamento(100L));
        when(repository.saveAndFlush(any(AgendaIdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("restricao unica violada"));
        when(agendaService.buscarPorId(clinicaA, 99L)).thenReturn(agendamento(99L));

        AgendaAgendamentoDTO resultado = service.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-concorrente", payload,
                agendaService::criarAgendamento);

        assertThat(resultado.idLocal()).isEqualTo(99L);
    }

    @Test
    @DisplayName("estado vive no repositorio, nao em memoria: nova instancia do service se comporta igual")
    void should_behave_identically_after_simulated_restart() {
        when(repository.findByClinicaIdAndOperacaoAndIdempotencyKey(1L, "CRIAR_AGENDAMENTO", "key-restart"))
                .thenReturn(Optional.of(registro(clinicaA, "key-restart", payload, 77L)));
        when(agendaService.buscarPorId(clinicaA, 77L)).thenReturn(agendamento(77L));

        N8nIdempotencyService novaInstanciaAposRestart = new N8nIdempotencyService(repository, agendaService);

        AgendaAgendamentoDTO resultado = novaInstanciaAposRestart.executarCriacaoIdempotente(
                clinicaA, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, "key-restart", payload,
                agendaService::criarAgendamento);

        assertThat(resultado.idLocal()).isEqualTo(77L);
        verify(agendaService, never()).criarAgendamento(any(), any());
    }

    private AgendaIdempotencyKey registro(Clinica clinica, String key, NovoAgendamentoRequest dados, Long agendamentoLocalId) {
        AgendaIdempotencyKey registro = new AgendaIdempotencyKey();
        registro.setClinica(clinica);
        registro.setOperacao(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO);
        registro.setIdempotencyKey(key);
        registro.setRequestHash(hashDeteste(dados));
        registro.setAgendamentoLocalId(agendamentoLocalId);
        return registro;
    }

    private String hashDeteste(NovoAgendamentoRequest dados) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            String canonico = String.join("|",
                    str(dados.pacienteId()), str(dados.pacienteCpf()), str(dados.profissionalId()),
                    str(dados.localId()), str(dados.timetableId()), str(dados.data()),
                    str(dados.horarioInicio()), str(dados.horarioFim()), str(dados.procedimentoId()),
                    str(dados.procedimentoNome()), str(dados.convenioId()), str(dados.observacao()));
            byte[] hashBytes = digest.digest(canonico.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String str(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private AgendaAgendamentoDTO agendamento(Long idLocal) {
        return new AgendaAgendamentoDTO(
                idLocal, "sch-1", "DARWIN", 1L, "Paciente Teste", "***.***.777-35",
                "prof-1", "Dra. Ana", null, "Consulta", null, null, "loc-1", "Unidade Centro",
                LocalDate.parse("2026-07-20"), "09:00", "09:30", "AGENDADO", "tt-1", null,
                "DARWIN", OffsetDateTime.parse("2026-07-19T12:00:00Z"), "SYNCED");
    }
}
