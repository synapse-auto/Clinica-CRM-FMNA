package com.synapse.clinicafemina.dto.uazap;

import jakarta.validation.constraints.NotNull;

/** Requisição do diagnóstico administrativo — nunca aceita token, telefone ou URL do frontend. */
public record UazapPictureDiagnosticoRequest(@NotNull Long pacienteId) {
}
