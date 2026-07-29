package com.synapse.clinicafemina.dto.paciente.importacao;

import java.util.List;

public record ImportacaoCsvContatoResumo(
        int totalRows,
        int valid,
        int existing,
        int duplicateInFile,
        int invalid,
        int toCreate,
        int totalErrors,
        boolean errorsTruncated,
        List<ImportacaoCsvContatoErro> errors
) {
}
