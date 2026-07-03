package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.FollowUpTemporary;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpTemporaryRepository extends JpaRepository<FollowUpTemporary, Long>,
        JpaSpecificationExecutor<FollowUpTemporary> {

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

}
