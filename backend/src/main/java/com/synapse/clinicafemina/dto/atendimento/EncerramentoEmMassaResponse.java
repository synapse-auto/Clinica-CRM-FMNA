package com.synapse.clinicafemina.dto.atendimento;

import java.time.OffsetDateTime;

public record EncerramentoEmMassaResponse(
        int encerrados,
        OffsetDateTime dataEncerramento
) {
}
