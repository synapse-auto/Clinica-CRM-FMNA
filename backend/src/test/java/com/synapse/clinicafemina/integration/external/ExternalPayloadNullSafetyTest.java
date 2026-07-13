package com.synapse.clinicafemina.integration.external;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPayloadNullSafetyTest {

    @Test
    void should_replace_null_payload_with_empty_map_in_all_external_dtos() {
        ExternalPatientDTO patient = new ExternalPatientDTO(
                "patient-1", null, null, null, null, null, null, null);
        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "appointment-1", "patient-1", null, null, null, null, null, null, null, null, null);
        ExternalClinicalNoteDTO note = new ExternalClinicalNoteDTO(
                "note-1", "patient-1", null, null, null);

        assertTrue(patient.payload().isEmpty());
        assertTrue(appointment.payload().isEmpty());
        assertTrue(note.payload().isEmpty());
    }

    @Test
    void should_preserve_null_values_in_external_payload_copy() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("campoOpcional", null);

        ExternalPatientDTO patient = new ExternalPatientDTO(
                "patient-1", null, null, null, null, null, null, source);

        source.put("alteradoDepois", "nao deve aparecer");
        assertTrue(patient.payload().containsKey("campoOpcional"));
        assertNull(patient.payload().get("campoOpcional"));
        assertFalse(patient.payload().containsKey("alteradoDepois"));
    }

    @Test
    void should_normalize_null_or_invalid_page_data_to_a_safe_list() {
        PageResult<String> nullPage = new PageResult<>(null, false, null);
        List<String> valuesWithNull = new ArrayList<>();
        valuesWithNull.add("valido");
        valuesWithNull.add(null);
        PageResult<String> pageWithNullItem = new PageResult<>(valuesWithNull, false, null);

        assertTrue(nullPage.data().isEmpty());
        assertEquals(List.of("valido"), pageWithNullItem.data());
    }
}
