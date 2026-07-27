package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.*;
import com.synapse.clinicafemina.dto.cancelamento.CancelamentoAgendamentoResponse;
import com.synapse.clinicafemina.dto.cancelamento.CancelarAgendamentoN8nRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.external.AgendaExternalProvider;
import com.synapse.clinicafemina.integration.external.AgendaProviderFactory;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.CancelamentoAgendamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CancelamentoAgendamentoService {
    public record ResultadoN8n(CancelamentoAgendamentoResponse response, boolean criado) { }
    private static final Set<String> ORIGENS = Set.of("LEMBRETE_NEGADO", "PEDIDO_DIRETO", "CRM_MANUAL", "N8N", "SISTEMA_EXTERNO");
    private final CancelamentoAgendamentoRepository repository;
    private final AgendamentoRepository agendamentoRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AgendaProviderFactory providerFactory;
    private final AgendamentoService agendamentoService;

    @Transactional
    public ResultadoN8n cancelarPorN8n(Clinica clinica, String key, CancelarAgendamentoN8nRequest request) {
        String motivo = normalizarMotivo(request.motivo());
        String origem = normalizarOrigem(request.origem());
        CancelamentoAgendamento existente = repository.findByClinicaIdAndIdempotencyKey(clinica.getId(), key).orElse(null);
        if (existente != null) {
            if (mesmoPayload(existente, request, motivo, origem)) return new ResultadoN8n(toResponse(existente), false);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key ja utilizada com outro cancelamento.");
        }
        Agendamento agendamento = agendamentoRepository.findByIdAndClinicaId(request.agendamentoId(), clinica.getId())
                .orElseThrow(() -> new NotFoundException("Agendamento nao encontrado"));
        Atendimento atendimento = validarAtendimento(clinica, request.atendimentoId(), agendamento);
        CancelamentoAgendamento cancelamento = novoRegistro(clinica, agendamento, atendimento, motivo, origem, key);
        repository.save(cancelamento);
        try {
            AgendaExternalProvider provider = providerFactory.getProvider(clinica.getExternalProvider());
            if (provider.supportsWriteOperations()) {
                provider.cancelarAgendamento(clinica, agendamento.getId(), motivo);
                concluir(cancelamento, "CANCELADO", "SINCRONIZADO", null);
            } else {
                agendamentoService.cancelar(clinica, agendamento.getId(), new com.synapse.clinicafemina.dto.agendamento.AgendamentoCancelRequest(motivo));
                concluir(cancelamento, "CANCELADO", "NAO_APLICAVEL", null);
            }
        } catch (RuntimeException exception) {
            concluir(cancelamento, "FALHA_CANCELAMENTO", "FALHA_TEMPORARIA", resumirErro(exception));
        }
        return new ResultadoN8n(toResponse(repository.save(cancelamento)), true);
    }

    @Transactional
    public void registrarCancelamentoManual(Clinica clinica, Long agendamentoId, String motivo) {
        Agendamento agendamento = agendamentoRepository.findByIdAndClinicaId(agendamentoId, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Agendamento nao encontrado"));
        if (repository.existsByClinicaIdAndAgendamentoIdAndOrigem(clinica.getId(), agendamentoId, "CRM_MANUAL")) return;
        CancelamentoAgendamento registro = novoRegistro(clinica, agendamento, null, normalizarMotivo(motivo), "CRM_MANUAL", null);
        concluir(registro, "CANCELADO", "NAO_APLICAVEL", null);
        repository.save(registro);
    }

    @Transactional(readOnly = true)
    public Page<CancelamentoAgendamentoResponse> listar(Clinica clinica, String busca, String origem, String statusCancelamento,
                                                          String statusSincronizacao, Long pacienteId, Long agendamentoId,
                                                          OffsetDateTime inicio, OffsetDateTime fim, Pageable pageable) {
        Specification<CancelamentoAgendamento> spec = (root, query, cb) -> cb.equal(root.get("clinica").get("id"), clinica.getId());
        if (origem != null && !origem.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("origem"), origem));
        if (statusCancelamento != null && !statusCancelamento.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("statusCancelamento"), statusCancelamento));
        if (statusSincronizacao != null && !statusSincronizacao.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("statusSincronizacao"), statusSincronizacao));
        if (pacienteId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("paciente").get("id"), pacienteId));
        if (agendamentoId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("agendamento").get("id"), agendamentoId));
        if (inicio != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("coletadoEm"), inicio));
        if (fim != null) spec = spec.and((root, query, cb) -> cb.lessThan(root.get("coletadoEm"), fim));
        if (busca != null && !busca.isBlank()) {
            String term = "%" + busca.trim().toUpperCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.upper(root.get("paciente").get("nomeBusca")), term),
                    cb.like(cb.upper(root.get("motivo")), term)));
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    private Atendimento validarAtendimento(Clinica clinica, Long atendimentoId, Agendamento agendamento) {
        if (atendimentoId == null) return null;
        Atendimento atendimento = atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Atendimento nao encontrado"));
        if (!atendimento.getPaciente().getId().equals(agendamento.getPaciente().getId())) {
            throw new BadRequestException("Atendimento nao pertence ao paciente do agendamento.");
        }
        return atendimento;
    }
    private CancelamentoAgendamento novoRegistro(Clinica clinica, Agendamento a, Atendimento atendimento, String motivo, String origem, String key) {
        CancelamentoAgendamento c = new CancelamentoAgendamento(); c.setClinica(clinica); c.setPaciente(a.getPaciente()); c.setAgendamento(a); c.setAtendimento(atendimento);
        c.setMotivo(motivo); c.setOrigem(origem); c.setExternalProvider(a.getExternalSource()); c.setExternalAgendamentoId(a.getExternalId()); c.setIdempotencyKey(key);
        c.setStatusCancelamento("COLETADO"); c.setStatusSincronizacao("PENDENTE"); c.setColetadoEm(OffsetDateTime.now()); return c;
    }
    private void concluir(CancelamentoAgendamento c, String status, String sync, String erro) { c.setStatusCancelamento(status); c.setStatusSincronizacao(sync); c.setMensagemErroSincronizacao(erro); }
    private String normalizarOrigem(String origem) { String value = origem == null ? "" : origem.trim().toUpperCase(); if (!ORIGENS.contains(value)) throw new BadRequestException("Origem de cancelamento invalida."); return value; }
    private String normalizarMotivo(String motivo) { String value = motivo == null ? "" : motivo.replaceAll("<[^>]*>", "").trim(); if (value.isBlank()) throw new BadRequestException("Motivo do cancelamento e obrigatorio."); if (value.length() > 2000) throw new BadRequestException("Motivo do cancelamento deve ter no maximo 2000 caracteres."); return value; }
    private boolean mesmoPayload(CancelamentoAgendamento c, CancelarAgendamentoN8nRequest r, String motivo, String origem) { return c.getAgendamento().getId().equals(r.agendamentoId()) && java.util.Objects.equals(c.getAtendimento() == null ? null : c.getAtendimento().getId(), r.atendimentoId()) && c.getMotivo().equals(motivo) && c.getOrigem().equals(origem); }
    private String resumirErro(RuntimeException exception) { String message = exception.getMessage(); return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.substring(0, Math.min(255, message.length())); }
    private CancelamentoAgendamentoResponse toResponse(CancelamentoAgendamento c) { Agendamento a = c.getAgendamento(); Paciente p = c.getPaciente(); return new CancelamentoAgendamentoResponse(c.getId(), p.getId(), p.getNome(), mascararTelefone(p.getTelefone()), a == null ? null : a.getId(), a == null ? null : a.getDataHoraInicio(), a == null ? null : a.getProfissionalNome(), a == null ? null : a.getServicoNome(), c.getMotivo(), c.getOrigem(), c.getStatusCancelamento(), c.getStatusSincronizacao(), c.getColetadoEm()); }
    private String mascararTelefone(String telefone) { if (telefone == null || telefone.length() < 4) return null; String digits = telefone.replaceAll("\\D", ""); return digits.length() < 4 ? null : "***" + digits.substring(digits.length() - 4); }
}
