package com.synapse.clinicafemina.integration.whatsapp.uazap;

import java.util.List;

/**
 * Resultado sanitizado de uma tentativa de enriquecimento de foto de perfil via UAZAP.
 *
 * <p>{@code fotoUrl} é a única informação sensível aqui — usada exclusivamente para persistência
 * interna ({@link UazapProfilePhotoEnrichmentService}). Ao mapear para a resposta do endpoint de
 * diagnóstico administrativo, {@code fotoUrl} NUNCA deve ser incluído.</p>
 */
public record UazapPictureEnrichmentOutcome(
        Integer statusHttp,
        String contentType,
        Integer bodyBytes,
        String formato,
        List<String> chaves,
        boolean possuiUrlHttps,
        boolean possuiQueryString,
        boolean possuiBase64,
        String fotoUrl,
        boolean fotoPersistida,
        String motivoNaoPersistida
) {

    /** Nenhuma chamada real foi feita à UAZAP (gate resolvido antes da requisição). */
    public static UazapPictureEnrichmentOutcome semTentativa(String motivo) {
        return new UazapPictureEnrichmentOutcome(
                null, null, null, null, List.of(), false, false, false, null, false, motivo);
    }

    public UazapPictureEnrichmentOutcome comFotoPersistida() {
        return new UazapPictureEnrichmentOutcome(
                statusHttp, contentType, bodyBytes, formato, chaves,
                possuiUrlHttps, possuiQueryString, possuiBase64, fotoUrl, true, motivoNaoPersistida);
    }
}
