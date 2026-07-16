package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Mensagem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    interface UltimaPreviaProjection {
        Long getAtendimentoId();

        String getConteudoPrevia();
    }

    /** Histórico paginado de mensagens de um atendimento (mais recentes primeiro). */
    @Query("""
            SELECT m FROM Mensagem m
            WHERE m.atendimento.id = :atendimentoId
              AND m.atendimento.clinica.id = :clinicaId
            ORDER BY m.dataHora DESC
            """)
    Page<Mensagem> findByAtendimentoIdAndClinicaId(@Param("atendimentoId") Long atendimentoId,
                                                   @Param("clinicaId") Long clinicaId,
                                                   Pageable pageable);

    Optional<Mensagem> findFirstByAtendimentoIdOrderByDataHoraDesc(Long atendimentoId);

    @Query("""
            SELECT MAX(m.dataHora) FROM Mensagem m
            WHERE m.atendimento.id = :atendimentoId
              AND m.atendimento.clinica.id = :clinicaId
              AND m.direcao = 'ENTRADA'
            """)
    Optional<OffsetDateTime> findUltimaMensagemEntradaEm(
            @Param("atendimentoId") Long atendimentoId,
            @Param("clinicaId") Long clinicaId
    );

    @Query("""
            SELECT MAX(m.dataHora) FROM Mensagem m
            WHERE m.atendimento.id = :atendimentoId
              AND m.atendimento.clinica.id = :clinicaId
              AND m.direcao = 'SAIDA'
              AND m.tipoMedia = 'TEMPLATE'
              AND (m.whatsappStatus IS NULL OR m.whatsappStatus <> 'FALHA')
            """)
    Optional<OffsetDateTime> findUltimoTemplateSaidaValidoEm(
            @Param("atendimentoId") Long atendimentoId,
            @Param("clinicaId") Long clinicaId
    );

    @Query(value = """
            SELECT DISTINCT ON (atendimento_id)
                   atendimento_id AS "atendimentoId",
                   conteudo_previa AS "conteudoPrevia"
            FROM mensagem
            WHERE atendimento_id IN (:atendimentoIds)
            ORDER BY atendimento_id, data_hora DESC
            """, nativeQuery = true)
    List<UltimaPreviaProjection> findUltimasPreviasByAtendimentoIds(
            @Param("atendimentoIds") Collection<Long> atendimentoIds
    );

    /** Localiza mensagem por ID externo do WhatsApp dentro da clínica resolvida no webhook. */
    @Query("""
            SELECT m FROM Mensagem m
            JOIN FETCH m.atendimento a
            LEFT JOIN FETCH a.atendentePrincipal
            WHERE a.clinica.id = :clinicaId
              AND m.whatsappMessageId = :whatsappMessageId
            """)
    Optional<Mensagem> findByClinicaIdAndWhatsappMessageId(@Param("clinicaId") Long clinicaId,
                                                           @Param("whatsappMessageId") String whatsappMessageId);

    @Query("""
            SELECT COUNT(m) FROM Mensagem m
            WHERE m.atendimento.clinica.id = :clinicaId
              AND m.dataHora >= :inicio
              AND m.dataHora < :fim
            """)
    long countByClinicaAndPeriodo(@Param("clinicaId") Long clinicaId,
                                  @Param("inicio") OffsetDateTime inicio,
                                  @Param("fim") OffsetDateTime fim);

    @Query(value = """
            SELECT EXTRACT(HOUR FROM data_hora AT TIME ZONE 'America/Sao_Paulo')::int AS hora, COUNT(*) AS total
            FROM mensagem m
            JOIN atendimento a ON a.id = m.atendimento_id
            WHERE a.clinica_id = :clinicaId
              AND m.data_hora >= :inicio
              AND m.data_hora < :fim
            GROUP BY hora
            ORDER BY hora
            """, nativeQuery = true)
    List<Object[]> countMensagensPorHora(@Param("clinicaId") Long clinicaId,
                                         @Param("inicio") OffsetDateTime inicio,
                                         @Param("fim") OffsetDateTime fim);

    /** Marca mensagens não lidas de um atendimento como lidas (zera contador no app). */
    @Modifying
    @Query("""
            UPDATE Mensagem m SET m.whatsappStatus = 'LIDA', m.lidaEm = :lidaEm
            WHERE m.atendimento.id = :atendimentoId
              AND m.atendimento.clinica.id = :clinicaId
              AND m.direcao = 'ENTRADA'
              AND m.lidaEm IS NULL
            """)
    int marcarComoLidas(@Param("atendimentoId") Long atendimentoId,
                        @Param("clinicaId") Long clinicaId,
                        @Param("lidaEm") OffsetDateTime lidaEm);
}
