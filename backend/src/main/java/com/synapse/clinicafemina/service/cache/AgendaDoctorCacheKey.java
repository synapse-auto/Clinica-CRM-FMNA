package com.synapse.clinicafemina.service.cache;

import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import java.time.OffsetDateTime;

public record AgendaDoctorCacheKey(
        Long clinicId,
        ExternalProviderType provider,
        OffsetDateTime inicio,
        OffsetDateTime fim
) implements ClinicScopedCacheKey {
}
