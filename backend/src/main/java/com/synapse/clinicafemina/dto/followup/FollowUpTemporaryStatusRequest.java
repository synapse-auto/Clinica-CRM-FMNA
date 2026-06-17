package com.synapse.clinicafemina.dto.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FollowUpTemporaryStatusRequest(
        @NotBlank @Size(max = 40) String status,
        String cancelReason
) {
}
