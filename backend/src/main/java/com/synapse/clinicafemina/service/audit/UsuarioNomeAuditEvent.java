package com.synapse.clinicafemina.service.audit;

import java.time.OffsetDateTime;

public record UsuarioNomeAuditEvent(
        String acao,
        Long executorId,
        Long usuarioAlvoId,
        Long clinicaId,
        String campo,
        OffsetDateTime ocorridoEm
) {
}
