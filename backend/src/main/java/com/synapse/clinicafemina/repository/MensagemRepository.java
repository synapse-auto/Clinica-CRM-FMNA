package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Mensagem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    /** Histórico paginado de mensagens de um atendimento (mais recentes primeiro). */
    @Query("SELECT m FROM Mensagem m WHERE m.atendimento.id = :atendimentoId ORDER BY m.dataHora DESC")
    Page<Mensagem> findByAtendimentoId(@Param("atendimentoId") Long atendimentoId, Pageable pageable);

    /** Localiza mensagem por ID externo do WhatsApp (idempotência no processamento do webhook). */
    Optional<Mensagem> findByWhatsappMessageId(String whatsappMessageId);

    /** Marca mensagens não lidas de um atendimento como lidas (zera contador no app). */
    @Modifying
    @Query("""
            UPDATE Mensagem m SET m.whatsappStatus = 'LIDA', m.lidaEm = CURRENT_TIMESTAMP
            WHERE m.atendimento.id = :atendimentoId
              AND m.direcao = 'SAIDA'
              AND (m.whatsappStatus IS NULL OR m.whatsappStatus != 'LIDA')
            """)
    int marcarComoLidas(@Param("atendimentoId") Long atendimentoId);
}
