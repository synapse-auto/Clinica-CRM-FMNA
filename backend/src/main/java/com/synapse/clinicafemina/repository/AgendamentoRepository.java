package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    Optional<Agendamento> findByIdAndClinicaId(Long id, Long clinicaId);

    Optional<Agendamento> findByClinicaIdAndExternalSourceAndExternalId(
            Long clinicaId,
            ExternalProviderType externalSource,
            String externalId);

    @Query("""
            SELECT COUNT(a) FROM Agendamento a
            WHERE a.clinica.id = :clinicaId
              AND a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
            """)
    long countByClinicaAndPeriodo(@Param("clinicaId") Long clinicaId,
                                  @Param("inicio") OffsetDateTime inicio,
                                  @Param("fim") OffsetDateTime fim);

    @Query("""
            SELECT COUNT(a) FROM Agendamento a
            WHERE a.clinica.id = :clinicaId
              AND a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
              AND a.confirmadoEm IS NULL
              AND a.canceladoEm IS NULL
              AND a.status IN ('AGENDADO', 'PENDENTE_CONFIRMACAO')
            """)
    long countPendentesByClinicaAndPeriodo(@Param("clinicaId") Long clinicaId,
                                           @Param("inicio") OffsetDateTime inicio,
                                           @Param("fim") OffsetDateTime fim);

    @Query(value = """
            SELECT CAST(data_hora_inicio AT TIME ZONE 'America/Sao_Paulo' AS date) AS dia, COUNT(*) AS total
            FROM agendamento
            WHERE clinica_id = :clinicaId
              AND data_hora_inicio >= :inicio
              AND data_hora_inicio < :fim
            GROUP BY dia
            ORDER BY dia
            """, nativeQuery = true)
    List<Object[]> countAgendamentosPorDia(@Param("clinicaId") Long clinicaId,
                                           @Param("inicio") OffsetDateTime inicio,
                                           @Param("fim") OffsetDateTime fim);

    @Query(value = """
            SELECT COALESCE(NULLIF(servico_nome, ''), tipo, 'Não informado') AS servico, COUNT(*) AS total
            FROM agendamento
            WHERE clinica_id = :clinicaId
              AND data_hora_inicio >= :inicio
              AND data_hora_inicio < :fim
            GROUP BY servico
            ORDER BY total DESC, servico ASC
            """, nativeQuery = true)
    List<Object[]> countServicosByClinicaAndPeriodo(@Param("clinicaId") Long clinicaId,
                                                    @Param("inicio") OffsetDateTime inicio,
                                                    @Param("fim") OffsetDateTime fim);
}
