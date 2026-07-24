package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parser defensivo da resposta do endpoint {@code getPicture} da UAZAP.
 *
 * <p><strong>O formato real da resposta ainda não foi confirmado em produção</strong> (o OpenAPI
 * oficial documenta apenas HTTP 201 com {@code content: {}} — corpo vazio). Este parser é
 * best-effort e estritamente limitado: considera SOMENTE os nomes de campo abaixo (nunca uma
 * string arbitrária do JSON), nunca inventa uma URL, e só aceita HTTPS sem query string,
 * fragmento ou userinfo — a mesma regra já aplicada ao perfil Meta em
 * {@code WhatsappInboundMapper.normalizarFotoUrl}. Atualize/reduza {@link #CAMPOS_URL_CANDIDATOS}
 * assim que o endpoint de diagnóstico administrativo
 * ({@code POST /api/admin/integracoes/uazap/foto/diagnostico}) confirmar os nomes reais das
 * chaves em produção.</p>
 */
@Component
public class UazapPicturePayloadParser {

    static final List<String> CAMPOS_URL_CANDIDATOS = List.of(
            "url", "pictureUrl", "profilePictureUrl", "photoUrl", "picture", "avatar", "photo", "link", "image"
    );

    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int BASE64_MIN_LENGTH = 200;
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    private final ObjectMapper objectMapper;

    public UazapPicturePayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UazapPictureEnrichmentOutcome parse(UazapPictureRawResponse raw) {
        int statusHttp = raw.statusCode();
        String contentType = raw.contentType();
        byte[] body = raw.body() == null ? new byte[0] : raw.body();
        int bodyBytes = body.length;

        if (bodyBytes == 0) {
            return outcome(statusHttp, contentType, bodyBytes, "VAZIO", List.of(), false, false, false, null, "CORPO_VAZIO");
        }
        if (bodyBytes > MAX_BODY_BYTES) {
            return outcome(statusHttp, contentType, bodyBytes, "EXCEDE_LIMITE", List.of(), false, false, false, null, "TAMANHO_EXCEDE_LIMITE");
        }
        if (statusHttp < 200 || statusHttp >= 300) {
            return outcome(statusHttp, contentType, bodyBytes, "ERRO_HTTP", List.of(), false, false, false, null, "STATUS_HTTP_" + statusHttp);
        }
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return outcome(statusHttp, contentType, bodyBytes, "IMAGEM", List.of(), false, false, false, null,
                    "RESPOSTA_BINARIA_SEM_ESTRATEGIA_DE_ARMAZENAMENTO");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception exception) {
            return outcome(statusHttp, contentType, bodyBytes, "INVALIDO", List.of(), false, false, false, null, "JSON_INVALIDO");
        }

        if (root == null || !root.isObject()) {
            return outcome(statusHttp, contentType, bodyBytes, "JSON", List.of(), false, false, false, null, "ESTRUTURA_JSON_NAO_SUPORTADA");
        }

        List<String> chaves = new ArrayList<>();
        root.fieldNames().forEachRemaining(chaves::add);
        boolean possuiBase64 = detectaBase64(root);

        String candidato = extrairCandidato(root);
        if (candidato == null) {
            return outcome(statusHttp, contentType, bodyBytes, "JSON", chaves, false, false, possuiBase64, null,
                    "NENHUM_CAMPO_DE_FOTO_RECONHECIDO");
        }

        UrlAvaliacao avaliacao = avaliarUrl(candidato);
        if (!avaliacao.valida()) {
            return outcome(statusHttp, contentType, bodyBytes, "JSON", chaves,
                    avaliacao.https(), avaliacao.possuiQueryString(), possuiBase64, null, avaliacao.motivoRejeicao());
        }
        return outcome(statusHttp, contentType, bodyBytes, "JSON", chaves,
                true, false, possuiBase64, avaliacao.urlNormalizada(), null);
    }

    private String extrairCandidato(JsonNode root) {
        for (String campo : CAMPOS_URL_CANDIDATOS) {
            JsonNode valor = root.get(campo);
            if (valor == null || valor.isNull()) {
                continue;
            }
            if (valor.isTextual() && !valor.asText().isBlank()) {
                return valor.asText();
            }
            if (valor.isObject() && valor.hasNonNull("url") && valor.get("url").isTextual()) {
                return valor.get("url").asText();
            }
        }
        return null;
    }

    private boolean detectaBase64(JsonNode root) {
        Iterator<JsonNode> valores = root.elements();
        while (valores.hasNext()) {
            JsonNode valor = valores.next();
            if (!valor.isTextual()) {
                continue;
            }
            String texto = valor.asText();
            if (texto.startsWith("data:") && texto.contains("base64,")) {
                return true;
            }
            if (texto.length() >= BASE64_MIN_LENGTH && BASE64_PATTERN.matcher(texto).matches()) {
                return true;
            }
        }
        return false;
    }

    private UrlAvaliacao avaliarUrl(String bruto) {
        URI uri;
        try {
            uri = new URI(bruto.trim());
        } catch (URISyntaxException exception) {
            return UrlAvaliacao.invalida(false, false, "URL_MAL_FORMADA");
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean possuiQuery = uri.getQuery() != null;
        if (!https) {
            return UrlAvaliacao.invalida(false, possuiQuery, "ESQUEMA_NAO_HTTPS");
        }
        if (uri.getUserInfo() != null) {
            return UrlAvaliacao.invalida(true, possuiQuery, "URL_CONTEM_USERINFO");
        }
        if (possuiQuery || uri.getFragment() != null) {
            return UrlAvaliacao.invalida(true, possuiQuery, "URL_POSSIVELMENTE_TEMPORARIA_OU_ASSINADA");
        }
        return UrlAvaliacao.valida(uri.toString());
    }

    private UazapPictureEnrichmentOutcome outcome(
            Integer statusHttp, String contentType, Integer bodyBytes, String formato, List<String> chaves,
            boolean possuiUrlHttps, boolean possuiQueryString, boolean possuiBase64, String fotoUrl, String motivo
    ) {
        return new UazapPictureEnrichmentOutcome(
                statusHttp, contentType, bodyBytes, formato, chaves,
                possuiUrlHttps, possuiQueryString, possuiBase64, fotoUrl, false, motivo);
    }

    private record UrlAvaliacao(boolean valida, boolean https, boolean possuiQueryString, String urlNormalizada, String motivoRejeicao) {
        static UrlAvaliacao valida(String url) {
            return new UrlAvaliacao(true, true, false, url, null);
        }

        static UrlAvaliacao invalida(boolean https, boolean possuiQueryString, String motivo) {
            return new UrlAvaliacao(false, https, possuiQueryString, null, motivo);
        }
    }
}
