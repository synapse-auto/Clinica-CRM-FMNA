package com.synapse.clinicafemina.dto.uazap;

import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapPictureEnrichmentOutcome;

import java.util.List;

/**
 * Resposta sanitizada do diagnóstico administrativo. Deliberadamente NÃO inclui {@code fotoUrl}:
 * nunca token, telefone, nome ou URL completa — apenas metadados estruturais.
 */
public record UazapPictureDiagnosticoResponse(
        Integer statusHttp,
        String contentType,
        Integer bodyBytes,
        String formato,
        List<String> chaves,
        boolean possuiUrlHttps,
        boolean possuiQueryString,
        boolean possuiBase64,
        boolean fotoPersistida,
        String motivoNaoPersistida,
        /** Caminhos de chaves + tipos (ex.: {@code "data.picture:string"}) — nunca valores, URLs, telefones ou nomes. */
        List<String> estrutura
) {
    public static UazapPictureDiagnosticoResponse from(UazapPictureEnrichmentOutcome outcome) {
        return new UazapPictureDiagnosticoResponse(
                outcome.statusHttp(),
                outcome.contentType(),
                outcome.bodyBytes(),
                outcome.formato(),
                outcome.chaves(),
                outcome.possuiUrlHttps(),
                outcome.possuiQueryString(),
                outcome.possuiBase64(),
                outcome.fotoPersistida(),
                outcome.motivoNaoPersistida(),
                outcome.estrutura()
        );
    }
}
