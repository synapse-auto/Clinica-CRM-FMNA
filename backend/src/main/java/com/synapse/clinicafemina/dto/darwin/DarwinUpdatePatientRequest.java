package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corpo de PUT /api/patients/update (coleção Postman Darwin v1.0.9).
 * "cpf" identifica o paciente e é obrigatório; "name" não existe neste DTO pois a
 * documentação afirma que o campo é ignorado pelo servidor (nome não pode ser alterado).
 * Campos ausentes (null) permanecem inalterados no Darwin — esta é uma simplificação
 * conhecida: esta versão não oferece um mecanismo para enviar null explícito e assim
 * limpar um campo já preenchido (ver relatório final).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DarwinUpdatePatientRequest(
        String cpf,
        String rg,
        String cns,
        String job,
        String email,
        String socialName,
        String motherName,
        String fatherName,
        String naturalness,
        String gender,
        String skinColor,
        String birthDate,
        String phoneNumber,
        List<DarwinExtraPhone> extraPhones,
        DarwinAddress address
) {}
