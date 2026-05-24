package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wrapper paginado genérico para respostas do Darwin API.
 */
public record DarwinPageResponse<T>(
        List<T> data,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("next_cursor") String nextCursor
) {}
