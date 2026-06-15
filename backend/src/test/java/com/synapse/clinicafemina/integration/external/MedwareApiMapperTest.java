package com.synapse.clinicafemina.integration.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MedwareApiMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MedwareApiMapper mapper = new MedwareApiMapper(objectMapper);

    @Test
    void should_map_patient_from_medware_payload() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "codPaciente": 1023,
                  "nome": "Maria Oliveira",
                  "cpf": "98765432100",
                  "email": "maria@example.test",
                  "dataNascimento": "25/08/1990",
                  "numeroCelularddd": "61",
                  "numeroCelular": "998877665"
                }
                """);

        ExternalPatientDTO dto = mapper.toPatient(payload);

        assertEquals("1023", dto.externalId());
        assertEquals("Maria Oliveira", dto.fullName());
        assertEquals("98765432100", dto.documentNumber());
        assertEquals("maria@example.test", dto.email());
        assertEquals("61998877665", dto.phone());
        assertEquals("25/08/1990", dto.birthDate());
        assertEquals("Maria Oliveira", dto.payload().get("nome"));
    }

    @Test
    void should_map_appointment_with_catalog_enrichment_when_available() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "codAgendamento": 555,
                  "codPaciente": 1023,
                  "codMedico": 7,
                  "codProcedimento": 15,
                  "dataHoraAgendada": "2026-06-10T09:30:00",
                  "codStatusAgendamento": 2
                }
                """);
        JsonNode procedimento = objectMapper.readTree("""
                {
                  "codProcedimento": 15,
                  "descricaoProcedimento": "Ultrassom obstetrico",
                  "duracao": 40,
                  "consulta": false
                }
                """);
        JsonNode medico = objectMapper.readTree("""
                {
                  "codMedico": 7,
                  "nome": "Dra. Ana"
                }
                """);

        ExternalAppointmentDTO dto = mapper.toAppointment(payload, Map.of("15", procedimento), Map.of("7", medico));

        assertEquals("555", dto.externalId());
        assertEquals("1023", dto.externalPatientId());
        assertEquals(OffsetDateTime.parse("2026-06-10T09:30:00-03:00"), dto.startAt());
        assertEquals(OffsetDateTime.parse("2026-06-10T10:10:00-03:00"), dto.endAt());
        assertEquals("EXAME", dto.type());
        assertEquals("Ultrassom obstetrico", dto.serviceName());
        assertEquals("CONFIRMADO", dto.status());
        assertEquals("Dra. Ana", dto.payload().get("medicoNome"));
        assertNotNull(dto.payload().get("medware"));
    }
}
