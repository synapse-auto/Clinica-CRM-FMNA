package com.synapse.clinicafemina.dto.uazap;

/** Contadores sem PII do reprocessamento administrativo de fotos UAZAP. */
public record UazapPictureReprocessarPendentesResponse(
        int selecionados,
        int processados,
        int fotosPersistidas,
        int semFoto,
        int falhasTemporarias,
        int falhasPermanentes
) {
}
