package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Atendimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    /** Lista atendimentos de uma clínica com filtros opcionais de status e tipo (IA/HUMANO). */
    @Query("""
            SELECT a FROM Atendimento a
            WHERE a.clinica.id = :clinicaId
              AND (:status IS NULL OR a.status = :status)
              AND (:tratadoPorIa IS NULL OR a.tratadoPorIa = :tratadoPorIa)
              AND (:atendenteId IS NULL OR a.atendentePrincipal.id = :atendenteId)
              AND (:somenteNaoLidos = false OR a.naoLidas > 0)
              AND (:somenteAguardando = false OR (a.status = 'ATIVO' AND a.atendentePrincipal IS NULL))
              AND (:somenteRevisao = false OR a.paciente.requerRevisao = true)
              AND (:busca = '' OR
                   a.paciente.nomeBusca LIKE CONCAT('%', :busca, '%') OR
                   a.paciente.telefoneNormalizado LIKE CONCAT('%', :busca, '%'))
            ORDER BY a.ultimaMensagemEm DESC NULLS LAST
            """)
    Page<Atendimento> findByClinica(
            @Param("clinicaId") Long clinicaId,
            @Param("status") String status,
            @Param("tratadoPorIa") Boolean tratadoPorIa,
            @Param("atendenteId") Long atendenteId,
            @Param("somenteNaoLidos") boolean somenteNaoLidos,
            @Param("somenteAguardando") boolean somenteAguardando,
            @Param("somenteRevisao") boolean somenteRevisao,
            @Param("busca") String busca,
            Pageable pageable
    );

    Optional<Atendimento> findByIdAndClinicaId(Long id, Long clinicaId);

    @Query("""
            SELECT a FROM Atendimento a
            LEFT JOIN FETCH a.atendentePrincipal
            JOIN FETCH a.paciente
            WHERE a.tratadoPorIa = false
              AND a.status = 'ATIVO'
              AND a.humanoDesde IS NOT NULL
              AND a.humanoDesde <= :limite
            """)
    List<Atendimento> findHumanosParaRetornoIa(@Param("limite") OffsetDateTime limite);

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

    @Query(value = """
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (saida.data_hora - entrada.data_hora)) / 60.0), 0)
            FROM mensagem entrada
            JOIN atendimento a ON a.id = entrada.atendimento_id
            JOIN LATERAL (
                SELECT m2.data_hora
                FROM mensagem m2
                WHERE m2.atendimento_id = entrada.atendimento_id
                  AND m2.direcao = 'SAIDA'
                  AND m2.data_hora > entrada.data_hora
                ORDER BY m2.data_hora ASC
                LIMIT 1
            ) saida ON TRUE
            WHERE a.clinica_id = :clinicaId
              AND entrada.direcao = 'ENTRADA'
              AND entrada.data_hora >= :inicio
              AND entrada.data_hora < :fim
            """, nativeQuery = true)
    Double calcularTempoMedioRespostaMinutos(@Param("clinicaId") Long clinicaId,
                                             @Param("inicio") OffsetDateTime inicio,
                                             @Param("fim") OffsetDateTime fim);
}
