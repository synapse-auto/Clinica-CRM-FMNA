package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    interface PacienteResumoProjection {
        Long getId();

        Long getClinicaId();

        String getNomeBusca();

        String getTelefoneNormalizado();

        String getStatus();

        String getExternalSource();

        String getExternalId();

        String getFotoUrl();

        Instant getCriadoEm();

        Instant getUltimaInteracaoEm();
    }

    interface PacienteOptionProjection {
        Long getId();

        String getNomeBusca();
    }

    interface PacienteStatusCountsProjection {
        Long getTotal();

        Long getEmAtendimento();

        Long getAgendado();

        Long getFinalizado();
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
                clinica_id AS "clinicaId",
                COALESCE(NULLIF(nome_busca, ''), CONCAT('Paciente ', id)) AS "nomeBusca",
                telefone_normalizado AS "telefoneNormalizado",
                status AS status,
                external_source AS "externalSource",
                external_id AS "externalId",
                foto_url AS "fotoUrl",
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
                p.id AS id,
                p.clinica_id AS "clinicaId",
                COALESCE(NULLIF(p.nome_busca, ''), CONCAT('Paciente ', p.id)) AS "nomeBusca",
                p.telefone_normalizado AS "telefoneNormalizado",
                p.status AS status,
                p.external_source AS "externalSource",
                p.external_id AS "externalId",
                p.foto_url AS "fotoUrl",
                p.criado_em AS "criadoEm",
                p.ultima_interacao_em AS "ultimaInteracaoEm"
            FROM paciente p
            WHERE p.clinica_id = :clinicaId
              AND p.deletado_em IS NULL
              AND (:status = '' OR p.status = :status)
              AND (:tagId IS NULL OR EXISTS (
                    SELECT 1
                    FROM paciente_tag pt
                    JOIN tag t ON t.id = pt.tag_id
                    WHERE pt.paciente_id = p.id
                      AND pt.tag_id = :tagId
                      AND t.clinica_id = :clinicaId
                      AND t.deletado_em IS NULL
              ))
              AND (
                    :mode = 0
                    OR (:exactId IS NOT NULL AND p.id = :exactId)
                    OR UPPER(COALESCE(p.external_id, '')) = :externalExact
                    OR (:mode = 2 AND (
                        p.telefone_normalizado = :digits
                        OR p.telefone_normalizado = :localPhone
                        OR p.telefone_normalizado = :phoneWithCountryCode
                    ))
                    OR (:mode = 1 AND
                        (:token1 = '' OR p.nome_busca LIKE CONCAT('%', :token1, '%')) AND
                        (:token2 = '' OR p.nome_busca LIKE CONCAT('%', :token2, '%')) AND
                        (:token3 = '' OR p.nome_busca LIKE CONCAT('%', :token3, '%')) AND
                        (:token4 = '' OR p.nome_busca LIKE CONCAT('%', :token4, '%')) AND
                        (:token5 = '' OR p.nome_busca LIKE CONCAT('%', :token5, '%'))
                    )
              )
            ORDER BY
              CASE
                WHEN :exactId IS NOT NULL AND p.id = :exactId THEN 0
                WHEN :mode = 2 AND p.telefone_normalizado IN (:digits, :localPhone, :phoneWithCountryCode) THEN 1
                WHEN UPPER(COALESCE(p.external_id, '')) = :externalExact THEN 2
                WHEN :mode = 1 AND p.nome_busca = :normalized THEN 3
                WHEN :mode = 1 AND p.nome_busca LIKE CONCAT(:normalized, '%') THEN 4
                ELSE 5
              END,
              p.ultima_interacao_em DESC NULLS LAST,
              p.nome_busca ASC,
              p.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM paciente p
            WHERE p.clinica_id = :clinicaId
              AND p.deletado_em IS NULL
              AND (:status = '' OR p.status = :status)
              AND (:tagId IS NULL OR EXISTS (
                    SELECT 1
                    FROM paciente_tag pt
                    JOIN tag t ON t.id = pt.tag_id
                    WHERE pt.paciente_id = p.id
                      AND pt.tag_id = :tagId
                      AND t.clinica_id = :clinicaId
                      AND t.deletado_em IS NULL
              ))
              AND (
                    :mode = 0
                    OR (:exactId IS NOT NULL AND p.id = :exactId)
                    OR UPPER(COALESCE(p.external_id, '')) = :externalExact
                    OR (:mode = 2 AND (
                        p.telefone_normalizado = :digits
                        OR p.telefone_normalizado = :localPhone
                        OR p.telefone_normalizado = :phoneWithCountryCode
                    ))
                    OR (:mode = 1 AND
                        (:token1 = '' OR p.nome_busca LIKE CONCAT('%', :token1, '%')) AND
                        (:token2 = '' OR p.nome_busca LIKE CONCAT('%', :token2, '%')) AND
                        (:token3 = '' OR p.nome_busca LIKE CONCAT('%', :token3, '%')) AND
                        (:token4 = '' OR p.nome_busca LIKE CONCAT('%', :token4, '%')) AND
                        (:token5 = '' OR p.nome_busca LIKE CONCAT('%', :token5, '%'))
                    )
              )
            """, nativeQuery = true)
    Page<PacienteResumoProjection> pesquisarResumos(
            @Param("clinicaId") Long clinicaId,
            @Param("status") String status,
            @Param("tagId") Long tagId,
            @Param("mode") int mode,
            @Param("normalized") String normalized,
            @Param("externalExact") String externalExact,
            @Param("digits") String digits,
            @Param("localPhone") String localPhone,
            @Param("phoneWithCountryCode") String phoneWithCountryCode,
            @Param("exactId") Long exactId,
            @Param("token1") String token1,
            @Param("token2") String token2,
            @Param("token3") String token3,
            @Param("token4") String token4,
            @Param("token5") String token5,
            Pageable pageable
    );

    @Query(value = """
            SELECT
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN status = 'EM_ATENDIMENTO' THEN 1 ELSE 0 END), 0) AS "emAtendimento",
                COALESCE(SUM(CASE WHEN status = 'AGENDADO' THEN 1 ELSE 0 END), 0) AS agendado,
                COALESCE(SUM(CASE WHEN status = 'FINALIZADO' THEN 1 ELSE 0 END), 0) AS finalizado
            FROM paciente
            WHERE clinica_id = :clinicaId
              AND deletado_em IS NULL
            """, nativeQuery = true)
    PacienteStatusCountsProjection countStatusByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query(value = """
            SELECT
                id AS id,
                clinica_id AS "clinicaId",
                COALESCE(NULLIF(nome_busca, ''), CONCAT('Paciente ', id)) AS "nomeBusca",
                telefone_normalizado AS "telefoneNormalizado",
                status AS status,
                external_source AS "externalSource",
                external_id AS "externalId",
                foto_url AS "fotoUrl",
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
