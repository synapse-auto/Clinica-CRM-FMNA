package com.synapse.clinicafemina.dto.uazap;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Lote administrativo sanitizado para recuperar fotos pendentes da clinica autenticada. */
public record UazapPictureReprocessarPendentesRequest(
        @Min(1) @Max(100) Integer limit
) {
    public int limiteEfetivo() {
        return limit == null ? 50 : limit;
    }
}
