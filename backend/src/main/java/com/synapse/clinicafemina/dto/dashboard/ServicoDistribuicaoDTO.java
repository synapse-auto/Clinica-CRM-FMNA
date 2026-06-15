package com.synapse.clinicafemina.dto.dashboard;

public record ServicoDistribuicaoDTO(
        String servico,
        long total,
        double percentual
) {
}
