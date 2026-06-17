package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.FollowUpTemporary;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpTemporaryRepository extends JpaRepository<FollowUpTemporary, Long> {

    Optional<FollowUpTemporary> findByIdAndClinicaId(Long id, Long clinicaId);

    @Query("""
            SELECT f FROM FollowUpTemporary f
            WHERE f.clinica.id = :clinicaId
              AND f.paciente.id = :pacienteId
            ORDER BY f.scheduledAt DESC
            """)
    Page<FollowUpTemporary> findByPaciente(@Param("clinicaId") Long clinicaId,
                                           @Param("pacienteId") Long pacienteId,
                                           Pageable pageable);

    @Query("""
            SELECT f FROM FollowUpTemporary f
            WHERE f.clinica.id = :clinicaId
              AND (:status IS NULL OR f.status = :status)
              AND (:pacienteId IS NULL OR f.paciente.id = :pacienteId)
              AND (:from IS NULL OR f.scheduledAt >= :from)
              AND (:to IS NULL OR f.scheduledAt < :to)
            ORDER BY f.scheduledAt ASC
            """)
    Page<FollowUpTemporary> findByClinica(@Param("clinicaId") Long clinicaId,
                                          @Param("status") String status,
                                          @Param("pacienteId") Long pacienteId,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to,
                                          Pageable pageable);
}
