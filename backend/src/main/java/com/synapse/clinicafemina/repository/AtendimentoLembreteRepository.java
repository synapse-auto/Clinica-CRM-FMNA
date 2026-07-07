package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.AtendimentoLembrete;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtendimentoLembreteRepository extends JpaRepository<AtendimentoLembrete, Long> {

    @Query("""
            SELECT lembrete
            FROM AtendimentoLembrete lembrete
            LEFT JOIN FETCH lembrete.criadoPor
            WHERE lembrete.atendimento.id = :atendimentoId
              AND lembrete.clinica.id = :clinicaId
            ORDER BY
              CASE
                WHEN lembrete.status = com.synapse.clinicafemina.domain.AtendimentoLembreteStatus.PENDENTE THEN 0
                WHEN lembrete.status = com.synapse.clinicafemina.domain.AtendimentoLembreteStatus.CONCLUIDO THEN 1
                ELSE 2
              END,
              lembrete.lembrarEm ASC
            """)
    List<AtendimentoLembrete> findByAtendimentoIdAndClinicaIdOrderByStatusAscLembrarEmAsc(
            @Param("atendimentoId") Long atendimentoId,
            @Param("clinicaId") Long clinicaId
    );

    @Query("""
            SELECT lembrete
            FROM AtendimentoLembrete lembrete
            LEFT JOIN FETCH lembrete.criadoPor
            WHERE lembrete.id = :id
              AND lembrete.atendimento.id = :atendimentoId
              AND lembrete.clinica.id = :clinicaId
            """)
    Optional<AtendimentoLembrete> findByIdAndAtendimentoIdAndClinicaId(
            @Param("id") Long id,
            @Param("atendimentoId") Long atendimentoId,
            @Param("clinicaId") Long clinicaId
    );
}
