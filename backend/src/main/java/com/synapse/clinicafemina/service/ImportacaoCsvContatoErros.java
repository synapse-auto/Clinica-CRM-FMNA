package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoErro;

import java.util.ArrayList;
import java.util.List;

final class ImportacaoCsvContatoErros {

    private static final int MAX_ERROR_DETAILS = 500;
    private final List<ImportacaoCsvContatoErro> details = new ArrayList<>();
    private int total;

    void add(int rowNumber, String field, String code, String message) {
        total++;
        if (details.size() < MAX_ERROR_DETAILS) {
            details.add(new ImportacaoCsvContatoErro(rowNumber, field, code, message));
        }
    }

    int total() {
        return total;
    }

    boolean truncated() {
        return total > details.size();
    }

    List<ImportacaoCsvContatoErro> details() {
        return List.copyOf(details);
    }
}
