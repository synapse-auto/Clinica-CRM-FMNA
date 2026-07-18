package com.synapse.clinicafemina.service.cache;

import java.time.OffsetDateTime;

public record DashboardCacheKey(
        Long clinicId,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String timezone,
        boolean incluirCirurgias
) implements ClinicScopedCacheKey {
}
