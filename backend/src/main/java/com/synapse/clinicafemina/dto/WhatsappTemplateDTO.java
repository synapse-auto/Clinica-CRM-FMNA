package com.synapse.clinicafemina.dto;

import java.util.List;

public record WhatsappTemplateDTO(
        String id,
        String nome,
        String idioma,
        String status,
        String categoria,
        String cabecalho,
        String corpo,
        String rodape,
        List<ButtonDTO> botoes,
        List<VariableDTO> variaveis,
        boolean suportado,
        String motivoNaoSuportado
) {
    public record ButtonDTO(String tipo, String texto, String url) {
    }

    public record VariableDTO(String componente, int posicao, Integer indiceBotao) {
    }
}
