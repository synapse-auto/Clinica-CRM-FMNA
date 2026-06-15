package com.synapse.clinicafemina.integration.external;

import java.util.List;

public record PageResult<T>(
        List<T> data,
        boolean hasMore,
        String nextCursor
) {
    public PageResult {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
