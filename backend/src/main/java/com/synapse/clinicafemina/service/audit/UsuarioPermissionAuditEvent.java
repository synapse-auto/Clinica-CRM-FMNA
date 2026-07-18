package com.synapse.clinicafemina.service.audit;

import java.time.OffsetDateTime;

public record UsuarioPermissionAuditEvent(
        String acao,
        Long executorId,
        Long usuarioAlvoId,
        Long clinicaId,
        boolean valorAnterior,
        boolean valorNovo,
        OffsetDateTime ocorridoEm
) {
}
