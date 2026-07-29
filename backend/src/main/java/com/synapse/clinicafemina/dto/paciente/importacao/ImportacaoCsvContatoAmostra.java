package com.synapse.clinicafemina.dto.paciente.importacao;

import java.util.List;

public record ImportacaoCsvContatoAmostra(int rowNumber, List<String> values) {
}
