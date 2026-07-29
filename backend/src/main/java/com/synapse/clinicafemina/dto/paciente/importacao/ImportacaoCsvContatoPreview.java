package com.synapse.clinicafemina.dto.paciente.importacao;

import java.util.List;

public record ImportacaoCsvContatoPreview(
        String fileHash,
        String fileName,
        String encoding,
        String delimiter,
        int totalRows,
        List<String> headers,
        ImportacaoCsvContatoMapping suggestedMapping,
        List<ImportacaoCsvContatoAmostra> sampleRows,
        List<String> warnings,
        ImportacaoCsvContatoResumo validation
) {
}
