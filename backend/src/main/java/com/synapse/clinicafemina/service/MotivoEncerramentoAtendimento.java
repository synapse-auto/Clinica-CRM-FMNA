package com.synapse.clinicafemina.service;

public final class MotivoEncerramentoAtendimento {

    public static final String PADRAO_MANUAL = "Encerrado manualmente pelo CRM";
    public static final String PADRAO_EM_MASSA = "Encerramento em massa pelo CRM";
    private static final int TAMANHO_MAXIMO = 255;

    private MotivoEncerramentoAtendimento() {
    }

    public static String sanitizar(String motivo, String padrao) {
        String texto = motivo == null ? "" : motivo
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String resultado = texto.isBlank() ? padrao : texto;
        return resultado.length() <= TAMANHO_MAXIMO
                ? resultado
                : resultado.substring(0, TAMANHO_MAXIMO);
    }
}
