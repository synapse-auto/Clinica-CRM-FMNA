package com.synapse.clinicafemina.dto.agenda;

import java.time.LocalDate;

public record NovoPacienteRequest(
        String cpf,
        String nome,
        String telefone,
        String email,
        LocalDate dataNascimento
) {}
