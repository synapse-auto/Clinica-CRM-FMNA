package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.CancelamentoAgendamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CancelamentoAgendamentoRepository extends JpaRepository<CancelamentoAgendamento, Long>, JpaSpecificationExecutor<CancelamentoAgendamento> {
    @EntityGraph(attributePaths = {"paciente", "agendamento", "atendimento"})
    Optional<CancelamentoAgendamento> findByClinicaIdAndIdempotencyKey(Long clinicaId, String idempotencyKey);
    boolean existsByClinicaIdAndAgendamentoIdAndOrigem(Long clinicaId, Long agendamentoId, String origem);
}
