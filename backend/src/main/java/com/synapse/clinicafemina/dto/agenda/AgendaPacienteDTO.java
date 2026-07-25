package com.synapse.clinicafemina.dto.agenda;

/**
 * Paciente normalizado para uso na Agenda. Sem endereço/telefone completo —
 * dados detalhados ficam restritos à ficha do paciente, não à listagem da Agenda.
 */
public record AgendaPacienteDTO(Long id, String nome, String cpfMascarado) {}
