package com.synapse.clinicafemina.dto;

import java.time.OffsetDateTime;

/** DTO de mensagem — corresponde ao payload STOMP {@code /user/queue/mensagens}. */
public record MensagemDTO(
        Long id,
        String direcao,
        String remetente,
        String tipoMedia,
        String conteudo,
        String conteudoPrevia,
        String whatsappStatus,
        OffsetDateTime dataHora,
        OffsetDateTime entregueEm,
        OffsetDateTime lidaEm
) {}
