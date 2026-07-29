package com.synapse.clinicafemina.integration.whatsapp.uazap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Referencia WhatsApp devolvida pela Uzapi; {@code id} e o wamid correlacionavel. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UazapMessageReference(String id) {
}
