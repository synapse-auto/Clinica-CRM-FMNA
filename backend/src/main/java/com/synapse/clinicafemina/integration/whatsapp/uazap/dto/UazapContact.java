package com.synapse.clinicafemina.integration.whatsapp.uazap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Contato resolvido pela Uzapi no aceite do envio. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UazapContact(
        String input,
        @JsonProperty("wa_id") String waId
) {
}
