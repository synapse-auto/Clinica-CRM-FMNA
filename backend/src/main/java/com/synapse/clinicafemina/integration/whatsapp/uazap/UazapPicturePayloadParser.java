package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class UazapPicturePayloadParser {

    static final List<String> CAMPOS_URL_CANDIDATOS = List.of(
            "url",
            "pictureUrl", "picture_url",
            "profilePictureUrl", "profile_picture_url",
            "profilePicUrl", "profile_pic_url",
            "photoUrl", "photo_url",
            "picture",
            "profilePicture", "profile_picture",
            "profilePic", "profile_pic",
            "avatar", "photo", "image", "link"
    );

    private static final String CAMPO_URL_GENERICO = "url";
    private static final String CHAVE_DATA = "data";
    private static final int PROFUNDIDADE_MAXIMA = 8;
    private static final int NOS_MAXIMOS = 500;
    private static final int CAMINHOS_MAXIMOS_ESTRUTURA = 100;
    private static final int MAX_RESPONSE_BYTES = 3 * 1024 * 1024;
    private static final int BASE64_MIN_LENGTH = 200;
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/\\r\\n]+={0,2}$");
    private static final Pattern DATA_URI_PATTERN =
            Pattern.compile("^data:([^;,]+);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Set<String> CAMPOS_FOTO_NORMALIZADOS = CAMPOS_URL_CANDIDATOS.stream()
            .map(UazapPicturePayloadParser::normalizarChave)
            .filter(chave -> !CAMPO_URL_GENERICO.equals(chave))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    private final ObjectMapper objectMapper;
    private final UazapProfilePhotoImageValidator imageValidator;

    public UazapPicturePayloadParser(
            ObjectMapper objectMapper,
            UazapProfilePhotoImageValidator imageValidator
    ) {
        this.objectMapper = objectMapper;
        this.imageValidator = imageValidator;
    }

    public UazapPictureEnrichmentOutcome parse(UazapPictureRawResponse raw) {
        return extract(raw).outcome();
    }

    public UazapPictureExtraction extract(UazapPictureRawResponse raw) {
        int statusHttp = raw.statusCode();
        String contentType = raw.contentType();
        byte[] body = raw.body() == null ? new byte[0] : raw.body();

        if (body.length == 0) {
            return semFonte(outcome(statusHttp, contentType, 0, "VAZIO", List.of(),
                    false, false, false, null, null, "CORPO_VAZIO", List.of()));
        }
        if (body.length > MAX_RESPONSE_BYTES) {
            return semFonte(outcome(statusHttp, contentType, body.length, "EXCEDE_LIMITE", List.of(),
                    false, false, false, null, null, "TAMANHO_EXCEDE_LIMITE", List.of()));
        }
        if (statusHttp < 200 || statusHttp >= 300) {
            return semFonte(outcome(statusHttp, contentType, body.length, "ERRO_HTTP", List.of(),
                    false, false, false, null, null, "STATUS_HTTP_" + statusHttp, List.of()));
        }
        if (isImageContentType(contentType)) {
            return extrairImagemBinaria(statusHttp, contentType, body);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception exception) {
            return semFonte(outcome(statusHttp, contentType, body.length, "INVALIDO", List.of(),
                    false, false, false, null, null, "JSON_INVALIDO", List.of()));
        }
        if (root == null || !root.isObject()) {
            return semFonte(outcome(statusHttp, contentType, body.length, "JSON", List.of(),
                    false, false, false, null, null, "ESTRUTURA_JSON_NAO_SUPORTADA", List.of()));
        }

        List<String> chaves = new ArrayList<>();
        root.fieldNames().forEachRemaining(chaves::add);
        List<String> estrutura = extrairEstrutura(root);
        String candidato = buscarCandidato(root, 0, false, null, new int[] {NOS_MAXIMOS});
        boolean possuiBase64 = detectaBase64(root, 0, new int[] {NOS_MAXIMOS});

        if (candidato == null) {
            return semFonte(outcome(statusHttp, contentType, body.length, "JSON", chaves,
                    false, false, possuiBase64, null, null,
                    "NENHUM_CAMPO_DE_FOTO_RECONHECIDO", estrutura));
        }
        if (pareceDataUri(candidato) || pareceBase64(candidato)) {
            return extrairBase64(statusHttp, contentType, body.length, chaves, estrutura, candidato);
        }
        return extrairUrl(statusHttp, contentType, body.length, chaves, estrutura, possuiBase64, candidato);
    }

    private UazapPictureExtraction extrairImagemBinaria(int statusHttp, String contentType, byte[] body) {
        try {
            UazapProfilePhotoImageValidator.ValidatedImage image = imageValidator.validar(body, contentType);
            UazapPictureEnrichmentOutcome result = outcome(
                    statusHttp, contentType, body.length, "IMAGEM", List.of(),
                    false, false, false, null, null, null, List.of());
            return new UazapPictureExtraction(
                    result,
                    UazapPictureSource.bytes(image.bytes(), image.contentType())
            );
        } catch (IllegalArgumentException exception) {
            return semFonte(outcome(statusHttp, contentType, body.length, "IMAGEM", List.of(),
                    false, false, false, null, null, exception.getMessage(), List.of()));
        }
    }

    private UazapPictureExtraction extrairBase64(
            int statusHttp,
            String contentType,
            int bodyBytes,
            List<String> chaves,
            List<String> estrutura,
            String candidato
    ) {
        String declaredContentType = null;
        String encoded = candidato.trim();
        Matcher dataUri = DATA_URI_PATTERN.matcher(encoded);
        if (dataUri.matches()) {
            declaredContentType = dataUri.group(1);
            encoded = dataUri.group(2);
        }

        try {
            if (encoded.length() > ((UazapProfilePhotoImageValidator.MAX_IMAGE_BYTES * 4L / 3L) + 16L)) {
                throw new IllegalArgumentException("IMAGEM_EXCEDE_LIMITE");
            }
            byte[] decoded = Base64.getMimeDecoder().decode(encoded);
            UazapProfilePhotoImageValidator.ValidatedImage image =
                    imageValidator.validar(decoded, declaredContentType);
            UazapPictureEnrichmentOutcome result = outcome(
                    statusHttp, contentType, bodyBytes, "BASE64", chaves,
                    false, false, true, null, null, null, estrutura);
            return new UazapPictureExtraction(
                    result,
                    UazapPictureSource.bytes(image.bytes(), image.contentType())
            );
        } catch (IllegalArgumentException exception) {
            return semFonte(outcome(statusHttp, contentType, bodyBytes, "BASE64", chaves,
                    false, false, true, null, null,
                    normalizarMotivoBase64(exception), estrutura));
        }
    }

    private UazapPictureExtraction extrairUrl(
            int statusHttp,
            String contentType,
            int bodyBytes,
            List<String> chaves,
            List<String> estrutura,
            boolean possuiBase64,
            String candidato
    ) {
        UrlAvaliacao avaliacao = avaliarUrl(candidato);
        if (!avaliacao.valida()) {
            return semFonte(outcome(statusHttp, contentType, bodyBytes, "JSON", chaves,
                    avaliacao.https(), avaliacao.possuiQueryString(), possuiBase64,
                    avaliacao.host(), null, avaliacao.motivoRejeicao(), estrutura));
        }

        UazapPictureEnrichmentOutcome result = outcome(
                statusHttp, contentType, bodyBytes, "JSON", chaves,
                true, avaliacao.possuiQueryString(), possuiBase64,
                avaliacao.host(), avaliacao.uri().toString(), null, estrutura);
        return new UazapPictureExtraction(result, UazapPictureSource.url(avaliacao.uri()));
    }

    private String buscarCandidato(
            JsonNode node,
            int profundidade,
            boolean dentroDeData,
            String chavePaiNormalizada,
            int[] orcamento
    ) {
        if (node == null || profundidade > PROFUNDIDADE_MAXIMA || orcamento[0] <= 0) {
            return null;
        }
        orcamento[0]--;

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> campos = node.fields();
            while (campos.hasNext()) {
                if (orcamento[0] <= 0) {
                    return null;
                }
                Map.Entry<String, JsonNode> campo = campos.next();
                String chaveNormalizada = normalizarChave(campo.getKey());
                JsonNode valor = campo.getValue();
                boolean novoDentroDeData = dentroDeData || CHAVE_DATA.equals(chaveNormalizada);

                if (valor.isTextual() && !valor.asText().isBlank()) {
                    if (CAMPO_URL_GENERICO.equals(chaveNormalizada)
                            && (dentroDeData || isCampoFotoRelacionado(chavePaiNormalizada))) {
                        return valor.asText();
                    }
                    if (isCampoFotoRelacionado(chaveNormalizada)) {
                        return valor.asText();
                    }
                }

                String encontrado = buscarCandidato(
                        valor,
                        profundidade + 1,
                        novoDentroDeData,
                        chaveNormalizada,
                        orcamento
                );
                if (encontrado != null) {
                    return encontrado;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode elemento : node) {
                String encontrado = buscarCandidato(
                        elemento,
                        profundidade + 1,
                        dentroDeData,
                        chavePaiNormalizada,
                        orcamento
                );
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private boolean detectaBase64(JsonNode node, int profundidade, int[] orcamento) {
        if (node == null || profundidade > PROFUNDIDADE_MAXIMA || orcamento[0] <= 0) {
            return false;
        }
        orcamento[0]--;

        if (node.isTextual()) {
            return pareceDataUri(node.asText()) || pareceBase64(node.asText());
        }
        for (JsonNode child : node) {
            if (detectaBase64(child, profundidade + 1, orcamento)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extrairEstrutura(JsonNode root) {
        Set<String> caminhos = new LinkedHashSet<>();
        coletarEstrutura(root, "", 0, caminhos, new int[] {NOS_MAXIMOS});
        return caminhos.stream().limit(CAMINHOS_MAXIMOS_ESTRUTURA).toList();
    }

    private void coletarEstrutura(
            JsonNode node,
            String caminho,
            int profundidade,
            Set<String> caminhos,
            int[] orcamento
    ) {
        if (node == null
                || profundidade > PROFUNDIDADE_MAXIMA
                || orcamento[0] <= 0
                || caminhos.size() >= CAMINHOS_MAXIMOS_ESTRUTURA) {
            return;
        }
        orcamento[0]--;

        if (node.isObject()) {
            node.fields().forEachRemaining(campo -> {
                if (orcamento[0] <= 0 || caminhos.size() >= CAMINHOS_MAXIMOS_ESTRUTURA) {
                    return;
                }
                String filho = caminho.isEmpty() ? campo.getKey() : caminho + "." + campo.getKey();
                registrarCampoEstrutura(campo.getValue(), filho, profundidade + 1, caminhos, orcamento);
            });
        } else if (node.isArray()) {
            for (JsonNode elemento : node) {
                coletarEstrutura(elemento, caminho, profundidade + 1, caminhos, orcamento);
            }
        }
    }

    private void registrarCampoEstrutura(
            JsonNode valor,
            String caminho,
            int profundidade,
            Set<String> caminhos,
            int[] orcamento
    ) {
        if (valor.isObject()) {
            caminhos.add(caminho + ":object");
            coletarEstrutura(valor, caminho, profundidade, caminhos, orcamento);
        } else if (valor.isArray()) {
            caminhos.add(caminho + ":array");
            coletarEstrutura(valor, caminho + "[]", profundidade, caminhos, orcamento);
        } else {
            caminhos.add(caminho + ":" + tipoDe(valor));
        }
    }

    private UrlAvaliacao avaliarUrl(String bruto) {
        URI uri;
        try {
            uri = new URI(bruto.trim());
        } catch (URISyntaxException exception) {
            return UrlAvaliacao.invalida(false, false, null, "URL_MAL_FORMADA");
        }

        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean possuiQuery = uri.getRawQuery() != null;
        if (!https) {
            return UrlAvaliacao.invalida(false, possuiQuery, uri.getHost(), "ESQUEMA_NAO_HTTPS");
        }
        if (uri.getUserInfo() != null) {
            return UrlAvaliacao.invalida(true, possuiQuery, uri.getHost(), "URL_CONTEM_USERINFO");
        }
        if (uri.getFragment() != null) {
            return UrlAvaliacao.invalida(true, possuiQuery, uri.getHost(), "URL_CONTEM_FRAGMENTO");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return UrlAvaliacao.invalida(true, possuiQuery, null, "URL_SEM_HOST");
        }
        return UrlAvaliacao.valida(uri.normalize(), possuiQuery);
    }

    private UazapPictureEnrichmentOutcome outcome(
            Integer statusHttp,
            String contentType,
            Integer bodyBytes,
            String formato,
            List<String> chaves,
            boolean possuiUrlHttps,
            boolean possuiQueryString,
            boolean possuiBase64,
            String hostFoto,
            String fotoUrl,
            String motivo,
            List<String> estrutura
    ) {
        return new UazapPictureEnrichmentOutcome(
                statusHttp,
                contentType,
                bodyBytes,
                formato,
                chaves,
                possuiUrlHttps,
                possuiQueryString,
                possuiBase64,
                hostFoto,
                fotoUrl,
                false,
                motivo,
                estrutura
        );
    }

    private UazapPictureExtraction semFonte(UazapPictureEnrichmentOutcome outcome) {
        return UazapPictureExtraction.semFonte(outcome);
    }

    private boolean isCampoFotoRelacionado(String value) {
        return value != null && CAMPOS_FOTO_NORMALIZADOS.contains(value);
    }

    private boolean isImageContentType(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private boolean pareceDataUri(String value) {
        return value != null && value.regionMatches(true, 0, "data:", 0, 5);
    }

    private boolean pareceBase64(String value) {
        if (value == null) {
            return false;
        }
        String compact = value.replaceAll("\\s", "");
        return compact.length() >= BASE64_MIN_LENGTH && BASE64_PATTERN.matcher(value).matches();
    }

    private String normalizarMotivoBase64(IllegalArgumentException exception) {
        String message = exception.getMessage();
        return message != null && message.matches("[A-Z_]+") ? message : "BASE64_INVALIDO";
    }

    private String tipoDe(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isBoolean()) return "boolean";
        if (node.isNumber()) return "number";
        if (node.isNull()) return "null";
        return "unknown";
    }

    private static String normalizarChave(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private record UrlAvaliacao(
            boolean valida,
            boolean https,
            boolean possuiQueryString,
            URI uri,
            String host,
            String motivoRejeicao
    ) {
        static UrlAvaliacao valida(URI uri, boolean possuiQueryString) {
            return new UrlAvaliacao(
                    true,
                    true,
                    possuiQueryString,
                    uri,
                    uri.getHost().toLowerCase(Locale.ROOT),
                    null
            );
        }

        static UrlAvaliacao invalida(
                boolean https,
                boolean possuiQueryString,
                String host,
                String motivo
        ) {
            String sanitizedHost = host == null ? null : host.toLowerCase(Locale.ROOT);
            return new UrlAvaliacao(false, https, possuiQueryString, null, sanitizedHost, motivo);
        }
    }
}
