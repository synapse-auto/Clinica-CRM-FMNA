package com.synapse.clinicafemina.dto.n8n;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record N8nResponderRequest(
        @NotNull Long pacienteId,
        @NotBlank @Size(max = 4096) String mensagem,
        @NotBlank String tipoMedia,
        @NotBlank String origem,
        Boolean enviarWhatsapp,
        @Size(max = 255, message = "whatsappMessageId deve ter no maximo 255 caracteres")
        String whatsappMessageId,
        OffsetDateTime enviadoEm
) {
    public N8nResponderRequest(
            Long pacienteId,
            String mensagem,
            String tipoMedia,
            String origem,
            Boolean enviarWhatsapp
    ) {
        this(pacienteId, mensagem, tipoMedia, origem, enviarWhatsapp, null, null);
    }
}
