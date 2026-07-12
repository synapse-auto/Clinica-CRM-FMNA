package com.synapse.clinicafemina.dto.agendamento;

public record AgendaOptionResponse(
        Long id,
        String nome,
        String codigoExterno,
        String origem
) {
    public AgendaOptionResponse(Long id, String nome) {
        this(id, nome, null, "CRM");
    }
}
