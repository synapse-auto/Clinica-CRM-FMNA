package com.synapse.clinicafemina.integration.external;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(
        List<T> data,
        boolean hasMore,
        String nextCursor
) {
    public PageResult {
        data = data == null
                ? List.of()
                : data.stream().filter(Objects::nonNull).toList();
    }
}
