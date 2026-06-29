package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    interface PacienteResumoProjection {
        Long getId();

        String getNomeBusca();

        String getTelefoneNormalizado();

        String getStatus();

        String getExternalSource();

        String getExternalId();

        Instant getCriadoEm();

        Instant getUltimaInteracaoEm();
    }

    interface PacienteOptionProjection {
        Long getId();

        String getNomeBusca();
    }

    Optional<Paciente> findByIdAndClinicaId(Long id, Long clinicaId);

    /** Localiza paciente pelo número E.164 normalizado (usado na integração WhatsApp). */
    Optional<Paciente> findByClinicaIdAndTelefoneNormalizado(Long clinicaId, String telefoneNormalizado);

    Optional<Paciente> findByCpfHash(String cpfHash);

    Optional<Paciente> findByClinicaIdAndCpfHash(Long clinicaId, String cpfHash);

    Optional<Paciente> findByClinicaIdAndExternalSourceAndExternalId(
            Long clinicaId,
            ExternalProviderType externalSource,
            String externalId);

    @Query("""
            SELECT p FROM Paciente p
            WHERE p.clinica.id = :clinicaId
              AND p.deletadoEm IS NULL
            ORDER BY p.nomeBusca ASC
            """)
    List<Paciente> findDisponiveisByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query(value = """
            SELECT
                id AS id,
                COALESCE(NULLIF(nome_busca, ''), CONCAT('Paciente ', id)) AS "nomeBusca",
                telefone_normalizado AS "telefoneNormalizado",
                status AS status,
                external_source AS "externalSource",
                external_id AS "externalId",
                criado_em AS "criadoEm",
                ultima_interacao_em AS "ultimaInteracaoEm"
            FROM paciente
            WHERE clinica_id = :clinicaId
              AND deletado_em IS NULL
            ORDER BY nome_busca ASC
            """, nativeQuery = true)
    List<PacienteResumoProjection> findResumosDisponiveisByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query(value = """
            SELECT
                id AS id,
                COALESCE(NULLIF(nome_busca, ''), CONCAT('Paciente ', id)) AS "nomeBusca",
                telefone_normalizado AS "telefoneNormalizado",
                status AS status,
                external_source AS "externalSource",
                external_id AS "externalId",
                criado_em AS "criadoEm",
                ultima_interacao_em AS "ultimaInteracaoEm"
            FROM paciente
            WHERE id = :id
              AND clinica_id = :clinicaId
              AND deletado_em IS NULL
            """, nativeQuery = true)
    Optional<PacienteResumoProjection> findResumoByIdAndClinicaId(
            @Param("id") Long id,
            @Param("clinicaId") Long clinicaId
    );

    @Query(value = """
            SELECT
                id AS id,
                COALESCE(NULLIF(nome_busca, ''), CONCAT('Paciente ', id)) AS "nomeBusca"
            FROM paciente
            WHERE clinica_id = :clinicaId
              AND deletado_em IS NULL
            ORDER BY nome_busca ASC
            """, nativeQuery = true)
    List<PacienteOptionProjection> findOpcoesDisponiveisByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query("""
            SELECT COUNT(p) FROM Paciente p
            WHERE p.clinica.id = :clinicaId
              AND p.criadoEm >= :inicio
              AND p.criadoEm < :fim
              AND p.deletadoEm IS NULL
            """)
    long countNovosPorClinicaAndPeriodo(@Param("clinicaId") Long clinicaId,
                                         @Param("inicio") OffsetDateTime inicio,
                                         @Param("fim") OffsetDateTime fim);

    @Query(value = """
            SELECT CAST(criado_em AT TIME ZONE 'America/Sao_Paulo' AS date) AS dia, COUNT(*) AS total
            FROM paciente
            WHERE clinica_id = :clinicaId
              AND criado_em >= :inicio
              AND criado_em < :fim
              AND deletado_em IS NULL
            GROUP BY dia
            ORDER BY dia
            """, nativeQuery = true)
    List<Object[]> countPacientesPorDia(@Param("clinicaId") Long clinicaId,
                                        @Param("inicio") OffsetDateTime inicio,
                                        @Param("fim") OffsetDateTime fim);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT paciente_id
                FROM atendimento
                WHERE clinica_id = :clinicaId
                  AND data_inicio >= :inicio
                  AND data_inicio < :fim
                GROUP BY paciente_id
                HAVING COUNT(*) > 1
            ) recorrentes
            """, nativeQuery = true)
    long countRecorrentesByClinicaAndPeriodo(@Param("clinicaId") Long clinicaId,
                                             @Param("inicio") OffsetDateTime inicio,
                                             @Param("fim") OffsetDateTime fim);
}
