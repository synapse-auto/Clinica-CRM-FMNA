package com.synapse.clinicafemina.dto.paciente.importacao;

public record ImportacaoCsvContatoErro(int rowNumber, String field, String code, String message) {
}
