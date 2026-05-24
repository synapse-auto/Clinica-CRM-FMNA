package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Atendimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    /** Lista atendimentos de uma clínica com filtros opcionais de status e tipo (IA/HUMANO). */
    @Query("""
            SELECT a FROM Atendimento a
            WHERE a.clinica.id = :clinicaId
              AND (:status IS NULL OR a.status = :status)
              AND (:tratadoPorIa IS NULL OR a.tratadoPorIa = :tratadoPorIa)
            ORDER BY a.ultimaMensagemEm DESC NULLS LAST
            """)
    Page<Atendimento> findByClinica(@Param("clinicaId") Long clinicaId,
                                    @Param("status") String status,
                                    @Param("tratadoPorIa") Boolean tratadoPorIa,
                                    Pageable pageable);

    /** Atendimento ativo de um paciente numa clínica. */
    @Query("""
            SELECT a FROM Atendimento a
            WHERE a.clinica.id = :clinicaId
              AND a.paciente.id = :pacienteId
              AND a.status = 'ATIVO'
            ORDER BY a.dataInicio DESC
            LIMIT 1
            """)
    Optional<Atendimento> findAtivo(@Param("clinicaId") Long clinicaId,
                                    @Param("pacienteId") Long pacienteId);

    /** Último atendimento (qualquer status) de um paciente. */
    @Query("""
            SELECT a FROM Atendimento a
            WHERE a.clinica.id = :clinicaId
              AND a.paciente.id = :pacienteId
            ORDER BY a.dataInicio DESC
            LIMIT 1
            """)
    Optional<Atendimento> findUltimo(@Param("clinicaId") Long clinicaId,
                                     @Param("pacienteId") Long pacienteId);

    /** Verifica se existe atendimento encerrado há menos de 24h para reuso de sessão. */
    @Query("""
            SELECT COUNT(a) > 0 FROM Atendimento a
            WHERE a.clinica.id = :clinicaId
              AND a.paciente.id = :pacienteId
              AND a.status = 'ENCERRADO'
              AND a.dataEncerramento > :desde
            """)
    boolean existeEncerradoDesde(@Param("clinicaId") Long clinicaId,
                                  @Param("pacienteId") Long pacienteId,
                                  @Param("desde") OffsetDateTime desde);
}
