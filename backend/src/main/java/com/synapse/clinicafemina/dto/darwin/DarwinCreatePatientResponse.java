package com.synapse.clinicafemina.dto.darwin;

/**
 * Corpo de resposta de POST /api/patients/create (200 já cadastrado / 201 criado —
 * mesmo formato em ambos, coleção Postman Darwin v1.0.9).
 */
public record DarwinCreatePatientResponse(String message, PatientSummary patient) {
    public record PatientSummary(String cpf, String name) {}
}
