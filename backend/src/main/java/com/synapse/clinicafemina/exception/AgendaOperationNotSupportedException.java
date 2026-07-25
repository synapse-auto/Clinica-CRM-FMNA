package com.synapse.clinicafemina.exception;

/**
 * Operação de Agenda genuinamente não suportada pelo provider atual nesta versão do
 * backend (ex.: catálogo sob demanda ou escrita para Medware, que não existe neste
 * codebase). Não é usada para Darwin, que suporta todas as operações desta interface.
 */
public class AgendaOperationNotSupportedException extends UnsupportedOperationException {

    public static final String CODE = "AGENDA_OPERACAO_NAO_SUPORTADA";

    public AgendaOperationNotSupportedException(String message) {
        super(message);
    }
}
