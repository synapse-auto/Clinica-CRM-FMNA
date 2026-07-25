package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.AgendaIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgendaIdempotencyKeyRepository extends JpaRepository<AgendaIdempotencyKey, Long> {

    Optional<AgendaIdempotencyKey> findByClinicaIdAndOperacaoAndIdempotencyKey(
            Long clinicaId, String operacao, String idempotencyKey);
}
