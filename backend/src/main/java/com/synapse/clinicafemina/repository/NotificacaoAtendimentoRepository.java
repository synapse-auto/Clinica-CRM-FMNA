package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.NotificacaoAtendimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface NotificacaoAtendimentoRepository extends JpaRepository<NotificacaoAtendimento, Long> {

    @Query("""
            SELECT n FROM NotificacaoAtendimento n
            WHERE n.usuario.id = :usuarioId
              AND (:somenteNaoLidas = false OR n.lidaEm IS NULL)
            ORDER BY n.criadoEm DESC
            """)
    Page<NotificacaoAtendimento> listar(
            @Param("usuarioId") Long usuarioId,
            @Param("somenteNaoLidas") boolean somenteNaoLidas,
            Pageable pageable
    );

    long countByUsuarioIdAndLidaEmIsNull(Long usuarioId);

    Optional<NotificacaoAtendimento> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByUsuarioIdAndMensagemIdAndTipo(Long usuarioId, Long mensagemId, String tipo);

    @Modifying
    @Query("""
            UPDATE NotificacaoAtendimento n SET n.lidaEm = :lidaEm
            WHERE n.usuario.id = :usuarioId AND n.lidaEm IS NULL
            """)
    int marcarTodasComoLidas(@Param("usuarioId") Long usuarioId, @Param("lidaEm") OffsetDateTime lidaEm);
}
