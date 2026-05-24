package com.synapse.clinicafemina.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo da requisição para transferir um atendimento. */
public record TransferirAtendimentoRequest(
        @NotBlank Long novoAtendenteId,
        @Size(max = 255) String motivo
) {}
