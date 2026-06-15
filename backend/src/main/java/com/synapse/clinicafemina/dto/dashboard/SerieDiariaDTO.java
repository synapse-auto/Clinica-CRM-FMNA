package com.synapse.clinicafemina.dto.dashboard;

import java.time.LocalDate;

public record SerieDiariaDTO(
        LocalDate data,
        long total
) {
}
