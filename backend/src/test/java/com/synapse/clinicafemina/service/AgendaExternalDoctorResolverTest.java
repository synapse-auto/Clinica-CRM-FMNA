package com.synapse.clinicafemina.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.junit.jupiter.api.Test;

class AgendaExternalDoctorResolverTest {

    private final AgendaExternalDoctorResolver resolver =
            new AgendaExternalDoctorResolver(new ObjectMapper());

    @Test
    void should_preserve_external_code_and_name_from_medware_payload() {
        Agendamento appointment = new Agendamento();
        appointment.setExternalSource(ExternalProviderType.MEDWARE);
        appointment.setExternalPayload("""
                {"codMedico":"M-17","medicoNome":"Dra. Renata Ávila"}
                """);

        var doctor = resolver.resolve(appointment);

        assertTrue(doctor.isPresent());
        assertEquals("M-17", doctor.get().codigoExterno());
        assertEquals("Dra. Renata Ávila", doctor.get().nome());
    }

    @Test
    void should_keep_same_names_with_different_external_codes_separate() {
        var first = new AgendaExternalDoctorResolver.ExternalDoctor("1", "Dra. Ana Lima");
        var second = new AgendaExternalDoctorResolver.ExternalDoctor("2", "Dra. Ana Lima");

        assertTrue(!resolver.key(first).equals(resolver.key(second)));
        assertEquals("dra. ana lima", resolver.normalize("Dra. Ána Lima"));
    }
}
