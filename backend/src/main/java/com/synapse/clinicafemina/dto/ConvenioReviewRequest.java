package com.synapse.clinicafemina.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConvenioReviewRequest(
        @NotBlank
        @Pattern(regexp = "APROVADO|RECUSADO|PENDENTE")
        String resultado
) {
}
