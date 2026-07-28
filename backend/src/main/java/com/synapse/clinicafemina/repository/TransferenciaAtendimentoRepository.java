package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.TransferenciaAtendimento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferenciaAtendimentoRepository extends JpaRepository<TransferenciaAtendimento, Long> {

    @Query("""
            SELECT t FROM TransferenciaAtendimento t
            JOIN FETCH t.atendimento a
            JOIN FETCH a.paciente
            LEFT JOIN FETCH a.atendentePrincipal
            WHERE a.clinica.id = :clinicaId
              AND t.origem = :origem
              AND t.idempotencyKey = :idempotencyKey
            """)
    Optional<TransferenciaAtendimento> findByClinicaIdAndOrigemAndIdempotencyKey(
            @Param("clinicaId") Long clinicaId,
            @Param("origem") String origem,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Query("""
            SELECT t.paraUsuario.id
            FROM TransferenciaAtendimento t
            WHERE t.atendimento.clinica.id = :clinicaId
              AND t.origem = :origem
              AND t.paraUsuario.id IN :atendentesIds
            ORDER BY t.transferidoEm DESC, t.id DESC
            """)
    List<Long> findDestinatariosPorOrigem(
            @Param("clinicaId") Long clinicaId,
            @Param("origem") String origem,
            @Param("atendentesIds") List<Long> atendentesIds,
            Pageable pageable
    );
}
