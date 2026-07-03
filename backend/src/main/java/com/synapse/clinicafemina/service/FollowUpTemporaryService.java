package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.FollowUpConfig;
import com.synapse.clinicafemina.domain.FollowUpTemporary;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryRequest;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryResponse;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryStatusRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.FollowUpConfigRepository;
import com.synapse.clinicafemina.repository.FollowUpTemporaryRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowUpTemporaryService {

    private static final String STATUS_PENDENTE = "PENDENTE";
    private static final String STATUS_PROCESSADO = "PROCESSADO";
    private static final String STATUS_EXECUTADO = "EXECUTADO";
    private static final String STATUS_CANCELADO = "CANCELADO";
    private static final String ORIGEM_MANUAL = "MANUAL";

    private final FollowUpTemporaryRepository followUpTemporaryRepository;
    private final PacienteRepository pacienteRepository;
    private final FollowUpConfigRepository followUpConfigRepository;
    private final N8nEventService n8nEventService;

    @Transactional(readOnly = true)
    public Page<FollowUpTemporaryResponse> listar(Clinica clinica, String status, Long pacienteId,
                                                  OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        return followUpTemporaryRepository.findAll(
                        filtros(clinica.getId(), validarStatusOpcional(status), pacienteId, from, to),
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FollowUpTemporaryResponse> listarPorPaciente(Clinica clinica, Long pacienteId, Pageable pageable) {
        buscarPacienteNaClinica(pacienteId, clinica.getId());
        return followUpTemporaryRepository.findByPaciente(clinica.getId(), pacienteId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public FollowUpTemporaryResponse criar(Clinica clinica, Long pacienteId, FollowUpTemporaryRequest request) {
        Paciente paciente = buscarPacienteNaClinica(pacienteId, clinica.getId());
        if (request.scheduledAt() == null) {
            throw new BadRequestException("scheduledAt e obrigatorio para follow-up temporario.");
        }

        FollowUpTemporary followUp = new FollowUpTemporary();
        followUp.setClinica(clinica);
        followUp.setPaciente(paciente);
        followUp.setFollowUpConfig(buscarConfigOpcional(request.followUpConfigId(), clinica.getId()));
        followUp.setTitulo(request.titulo());
        followUp.setDescricao(request.descricao());
        followUp.setOrigem(AutomacaoValidation.opcaoPadrao(
                request.origem(),
                ORIGEM_MANUAL,
                AutomacaoValidation.ORIGENS,
                "Origem"
        ));
        followUp.setStatus(AutomacaoValidation.opcaoPadrao(
                request.status(),
                STATUS_PENDENTE,
                AutomacaoValidation.STATUS_FOLLOW_UP,
                "Status"
        ));
        followUp.setScheduledAt(request.scheduledAt());
        followUp.setPayload(request.payload());

        FollowUpTemporary saved = followUpTemporaryRepository.save(followUp);
        emitirEvento(clinica, "follow_up_criado", saved);
        return toResponse(saved);
    }

    @Transactional
    public FollowUpTemporaryResponse alterarStatus(Clinica clinica, Long id, FollowUpTemporaryStatusRequest request) {
        FollowUpTemporary followUp = followUpTemporaryRepository.findByIdAndClinicaId(id, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Follow-up temporário não encontrado"));
        String status = validarStatus(request.status());
        followUp.setStatus(status);

        if (STATUS_PROCESSADO.equals(status) || STATUS_EXECUTADO.equals(status)) {
            followUp.setProcessedAt(OffsetDateTime.now());
            followUp.setCanceledAt(null);
            followUp.setCancelReason(null);
        } else if (STATUS_CANCELADO.equals(status)) {
            followUp.setCanceledAt(OffsetDateTime.now());
            followUp.setCancelReason(request.cancelReason());
        }

        FollowUpTemporary saved = followUpTemporaryRepository.save(followUp);
        if (STATUS_PROCESSADO.equals(status) || STATUS_EXECUTADO.equals(status)) {
            emitirEvento(clinica, "follow_up_executado", saved);
        } else if (STATUS_CANCELADO.equals(status)) {
            emitirEvento(clinica, "follow_up_cancelado", saved);
        }
        return toResponse(saved);
    }

    private Paciente buscarPacienteNaClinica(Long pacienteId, Long clinicaId) {
        return pacienteRepository.findByIdAndClinicaId(pacienteId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado para a clínica atual"));
    }

    private FollowUpConfig buscarConfigOpcional(Long configId, Long clinicaId) {
        if (configId == null) {
            return null;
        }
        return followUpConfigRepository.findByIdAndClinicaId(configId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Configuração de follow-up não encontrada"));
    }

    private void emitirEvento(Clinica clinica, String evento, FollowUpTemporary followUp) {
        Paciente paciente = followUp.getPaciente();
        N8nEventPayload payload = n8nEventService.criarPayload(
                clinica,
                evento,
                paciente.getId(),
                null,
                null,
                paciente.getTelefoneNormalizado()
        );
        n8nEventService.emitir(payload);
    }

    private FollowUpTemporaryResponse toResponse(FollowUpTemporary followUp) {
        return new FollowUpTemporaryResponse(
                followUp.getId(),
                followUp.getClinica().getId(),
                followUp.getPaciente().getId(),
                followUp.getFollowUpConfig() == null ? null : followUp.getFollowUpConfig().getId(),
                followUp.getTitulo(),
                followUp.getDescricao(),
                followUp.getOrigem(),
                followUp.getStatus(),
                followUp.getScheduledAt(),
                followUp.getProcessedAt(),
                followUp.getCanceledAt(),
                followUp.getCancelReason(),
                followUp.getPayload(),
                followUp.getCreatedAt(),
                followUp.getUpdatedAt()
        );
    }

    private String validarStatusOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return validarStatus(valor);
    }

    private String validarStatus(String valor) {
        return AutomacaoValidation.opcao(
                valor,
                AutomacaoValidation.STATUS_FOLLOW_UP,
                "Status"
        );
    }

    private Specification<FollowUpTemporary> filtros(Long clinicaId, String status, Long pacienteId,
                                                     OffsetDateTime from, OffsetDateTime to) {
        return (root, query, criteriaBuilder) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(criteriaBuilder.asc(root.get("scheduledAt")));
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("clinica").get("id"), clinicaId));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (pacienteId != null) {
                predicates.add(criteriaBuilder.equal(root.get("paciente").get("id"), pacienteId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("scheduledAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
