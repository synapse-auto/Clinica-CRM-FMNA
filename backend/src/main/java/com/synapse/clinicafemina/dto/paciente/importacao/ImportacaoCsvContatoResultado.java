package com.synapse.clinicafemina.dto.paciente.importacao;

import java.util.List;

public record ImportacaoCsvContatoResultado(
        int totalRows,
        int created,
        int skippedExisting,
        int skippedDuplicateInFile,
        int invalid,
        int totalErrors,
        boolean errorsTruncated,
        List<ImportacaoCsvContatoErro> errors
) {
}
