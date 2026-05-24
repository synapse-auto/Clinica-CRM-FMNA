package com.synapse.clinicafemina.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo da requisição para envio de mensagem outbound. */
public record EnviarMensagemRequest(
        @NotBlank String tipoMedia,
        @NotBlank @Size(max = 4096) String conteudo
) {}
