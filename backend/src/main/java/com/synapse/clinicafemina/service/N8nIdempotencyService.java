package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.AgendaIdempotencyKey;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.IdempotencyConflictException;
import com.synapse.clinicafemina.repository.AgendaIdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Idempotência explícita (por chave, nunca por conteúdo) para as escritas de criação
 * da Agenda expostas ao N8N. Estado persistido em {@code agenda_idempotency_key}
 * (restrição única por clinica_id+operacao+idempotency_key), não em memória — sobrevive
 * a restart. Mesma chave + mesmo payload retorna o resultado original; mesma chave +
 * payload diferente é rejeitada com 409 via {@link IdempotencyConflictException}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class N8nIdempotencyService {

    public static final String OPERACAO_CRIAR_AGENDAMENTO = "CRIAR_AGENDAMENTO";
    public static final String OPERACAO_CRIAR_ENCAIXE = "CRIAR_ENCAIXE";

    private final AgendaIdempotencyKeyRepository repository;
    private final AgendaService agendaService;

    @Transactional
    public AgendaAgendamentoDTO executarCriacaoIdempotente(
            Clinica clinica,
            String operacao,
            String idempotencyKey,
            NovoAgendamentoRequest dados,
            BiFunction<Clinica, NovoAgendamentoRequest, AgendaAgendamentoDTO> criador
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Cabeçalho Idempotency-Key é obrigatório.");
        }
        String hash = calcularHash(dados);

        Optional<AgendaIdempotencyKey> existente = repository
                .findByClinicaIdAndOperacaoAndIdempotencyKey(clinica.getId(), operacao, idempotencyKey);
        if (existente.isPresent()) {
            return resolverExistente(clinica, existente.get(), hash);
        }

        AgendaAgendamentoDTO criado = criador.apply(clinica, dados);
        try {
            salvarRegistro(clinica, operacao, idempotencyKey, hash, criado.idLocal());
        } catch (DataIntegrityViolationException concorrencia) {
            // Duas requisicoes concorrentes com a mesma chave: a que perdeu a corrida de
            // insert (restricao unica) retorna o resultado da vencedora, sem duplicar.
            AgendaIdempotencyKey registroConcorrente = repository
                    .findByClinicaIdAndOperacaoAndIdempotencyKey(clinica.getId(), operacao, idempotencyKey)
                    .orElseThrow(() -> concorrencia);
            return resolverExistente(clinica, registroConcorrente, hash);
        }
        return criado;
    }

    private AgendaAgendamentoDTO resolverExistente(Clinica clinica, AgendaIdempotencyKey registro, String hash) {
        if (!registro.getRequestHash().equals(hash)) {
            log.warn("Idempotency-Key reutilizada com payload diferente. clinicaId={} operacao={}",
                    clinica.getId(), registro.getOperacao());
            throw new IdempotencyConflictException(
                    "Esta Idempotency-Key já foi usada com dados diferentes para esta operação.");
        }
        return agendaService.buscarPorId(clinica, registro.getAgendamentoLocalId());
    }

    private void salvarRegistro(
            Clinica clinica, String operacao, String idempotencyKey, String hash, Long agendamentoLocalId) {
        AgendaIdempotencyKey registro = new AgendaIdempotencyKey();
        registro.setClinica(clinica);
        registro.setOperacao(operacao);
        registro.setIdempotencyKey(idempotencyKey);
        registro.setRequestHash(hash);
        registro.setAgendamentoLocalId(agendamentoLocalId);
        repository.saveAndFlush(registro);
    }

    private String calcularHash(NovoAgendamentoRequest dados) {
        String canonico = String.join("|",
                str(dados.pacienteId()), str(dados.pacienteCpf()), str(dados.profissionalId()),
                str(dados.localId()), str(dados.timetableId()), str(dados.data()),
                str(dados.horarioInicio()), str(dados.horarioFim()), str(dados.procedimentoId()),
                str(dados.procedimentoNome()), str(dados.convenioId()), str(dados.observacao()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM.", e);
        }
    }

    private String str(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
