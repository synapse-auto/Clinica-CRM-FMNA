package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.exception.BadRequestException;
import java.util.List;
import java.util.Locale;

final class AutomacaoValidation {

    static final List<String> CANAIS = List.of("WHATSAPP", "EMAIL", "SMS", "TELEFONE", "INTERNO");
    static final List<String> DELAY_UNIDADES = List.of("MINUTOS", "HORAS", "DIAS", "SEMANAS", "MESES");
    static final List<String> ANTECEDENCIA_UNIDADES = List.of("MINUTOS", "HORAS", "DIAS", "SEMANAS");
    static final List<String> ORIGENS = List.of(
            "MANUAL",
            "FOLLOW_UP_CONFIG",
            "CONSULTA_LEMBRETE",
            "MENSAGEM_FESTIVA",
            "N8N",
            "SISTEMA"
    );
    static final List<String> STATUS_FOLLOW_UP = List.of(
            "PENDENTE",
            "PROCESSANDO",
            "PROCESSADO",
            "EXECUTADO",
            "CANCELADO",
            "FALHOU"
    );

    private AutomacaoValidation() {
    }

    static String normalizar(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    static String opcaoPadrao(String valor, String padrao, List<String> permitidos, String label) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        return opcao(valor, permitidos, label);
    }

    static String opcaoOpcional(String valor, List<String> permitidos, String label) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return opcao(valor, permitidos, label);
    }

    static String opcao(String valor, List<String> permitidos, String label) {
        String normalizado = normalizar(valor);
        if (!permitidos.contains(normalizado)) {
            throw new BadRequestException(label + " deve ser um destes valores: " + String.join(", ", permitidos) + ".");
        }
        return normalizado;
    }
}
