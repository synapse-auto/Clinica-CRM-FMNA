package com.synapse.clinicafemina.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo da requisição para transferir um atendimento. */
public record TransferirAtendimentoRequest(
        @NotNull Long novoAtendenteId,
        @Size(max = 255) String motivo
) {}
