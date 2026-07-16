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
        String motivoFalha,
        OffsetDateTime dataHora,
        OffsetDateTime entregueEm,
        OffsetDateTime lidaEm,
        MidiaDTO midia,
        String templateNome,
        String templateIdioma
) {
    public MensagemDTO(
            Long id,
            String direcao,
            String remetente,
            String tipoMedia,
            String conteudo,
            String conteudoPrevia,
            String whatsappStatus,
            String motivoFalha,
            OffsetDateTime dataHora,
            OffsetDateTime entregueEm,
            OffsetDateTime lidaEm,
            MidiaDTO midia
    ) {
        this(id, direcao, remetente, tipoMedia, conteudo, conteudoPrevia,
                whatsappStatus, motivoFalha, dataHora, entregueEm, lidaEm, midia, null, null);
    }

    public record MidiaDTO(
            String tipoMedia,
            String mimeType,
            String nomeArquivo,
            Long tamanhoBytes,
            String url
    ) {}
}
