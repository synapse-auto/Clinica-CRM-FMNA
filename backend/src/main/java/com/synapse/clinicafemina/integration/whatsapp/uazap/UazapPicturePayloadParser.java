package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parser defensivo da resposta do endpoint {@code getPicture} da UAZAP.
 *
 * <p>Confirmado em produção via o endpoint de diagnóstico administrativo: a resposta real é um
 * envelope {@code {"status": ..., "data": ...}} — o campo de foto fica ANINHADO dentro de
 * {@code data} (objeto ou array), nunca na raiz. Por isso a busca é recursiva (objetos e arrays,
 * profundidade e quantidade de nós limitadas — ver {@link #PROFUNDIDADE_MAXIMA} e
 * {@link #NOS_MAXIMOS}), mas continua estritamente limitada aos nomes de campo em
 * {@link #CAMPOS_URL_CANDIDATOS} (nunca uma string arbitrária do JSON) e nunca inventa uma URL.
 * Só aceita HTTPS sem query string, fragmento ou userinfo — mesma regra já aplicada ao perfil Meta
 * em {@code WhatsappInboundMapper.normalizarFotoUrl}.</p>
 */
@Component
public class UazapPicturePayloadParser {

    /**
     * Nomes de campo (documentados, centralizados) considerados relacionados a foto. Comparados de
     * forma defensiva — case-insensitive e ignorando separadores ({@code _}/{@code -}) — então
     * {@code profilePictureUrl}, {@code profile_picture_url} e {@code profile-picture-url} casam
     * com a mesma entrada normalizada. Inclui as formas "contêiner" ({@code profilePicture},
     * {@code profilePic}) para suportar {@code {"profilePicture": {"url": "..."}}}, confirmado
     * como formato suportado nesta correção.
     */
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

    /** Chave especial: só é aceita quando dentro de {@code data} ou de um objeto pai relacionado a foto. */
    private static final String CAMPO_URL_GENERICO = "url";

    /** Chave do envelope de resposta confirmado em produção ({@code {"data": ...}}). */
    private static final String CHAVE_DATA = "data";

    private static final Set<String> CAMPOS_FOTO_NORMALIZADOS = CAMPOS_URL_CANDIDATOS.stream()
            .map(UazapPicturePayloadParser::normalizarChave)
            .filter(chave -> !CAMPO_URL_GENERICO.equals(chave))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    /** Profundidade máxima de recursão (objetos/arrays aninhados) — constante segura contra payloads abusivos. */
    private static final int PROFUNDIDADE_MAXIMA = 8;

    /** Quantidade máxima de nós visitados por travessia — protege contra payload malicioso "largo". */
    private static final int NOS_MAXIMOS = 500;

    /** Máximo de caminhos reportados na estrutura sanitizada do diagnóstico. */
    private static final int CAMINHOS_MAXIMOS_ESTRUTURA = 100;

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
            return outcome(statusHttp, contentType, bodyBytes, "VAZIO", List.of(), false, false, false, null, "CORPO_VAZIO", List.of());
        }
        if (bodyBytes > MAX_BODY_BYTES) {
            return outcome(statusHttp, contentType, bodyBytes, "EXCEDE_LIMITE", List.of(), false, false, false, null, "TAMANHO_EXCEDE_LIMITE", List.of());
        }
        if (statusHttp < 200 || statusHttp >= 300) {
            return outcome(statusHttp, contentType, bodyBytes, "ERRO_HTTP", List.of(), false, false, false, null, "STATUS_HTTP_" + statusHttp, List.of());
        }
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return outcome(statusHttp, contentType, bodyBytes, "IMAGEM", List.of(), false, false, false, null,
                    "RESPOSTA_BINARIA_SEM_ESTRATEGIA_DE_ARMAZENAMENTO", List.of());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception exception) {
            return outcome(statusHttp, contentType, bodyBytes, "INVALIDO", List.of(), false, false, false, null, "JSON_INVALIDO", List.of());
        }

        if (root == null || !root.isObject()) {
            return outcome(statusHttp, contentType, bodyBytes, "JSON", List.of(), false, false, false, null, "ESTRUTURA_JSON_NAO_SUPORTADA", List.of());
        }

        List<String> chaves = new ArrayList<>();
        root.fieldNames().forEachRemaining(chaves::add);
        boolean possuiBase64 = detectaBase64(root, 0, new int[] {NOS_MAXIMOS});
        List<String> estrutura = extrairEstrutura(root);

        String candidato = buscarCandidato(root, 0, false, null, new int[] {NOS_MAXIMOS});
        if (candidato == null) {
            return outcome(statusHttp, contentType, bodyBytes, "JSON", chaves, false, false, possuiBase64, null,
                    "NENHUM_CAMPO_DE_FOTO_RECONHECIDO", estrutura);
        }

        UrlAvaliacao avaliacao = avaliarUrl(candidato);
        if (!avaliacao.valida()) {
            return outcome(statusHttp, contentType, bodyBytes, "JSON", chaves,
                    avaliacao.https(), avaliacao.possuiQueryString(), possuiBase64, null, avaliacao.motivoRejeicao(), estrutura);
        }
        return outcome(statusHttp, contentType, bodyBytes, "JSON", chaves,
                true, false, possuiBase64, avaliacao.urlNormalizada(), null, estrutura);
    }

    /**
     * Busca recursiva (objetos e arrays) do primeiro valor de foto válido. A chave genérica
     * {@code "url"} só é aceita dentro de {@code data} (o envelope confirmado em produção) ou
     * dentro de um objeto cuja própria chave já é relacionada a foto — nunca uma URL solta de
     * documentação/instância/status. Limitada por profundidade e orçamento de nós.
     */
    private String buscarCandidato(
            JsonNode node, int profundidade, boolean dentroDeData, String chavePaiNormalizada, int[] orcamento
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

                if (CAMPO_URL_GENERICO.equals(chaveNormalizada)) {
                    if ((dentroDeData || isCampoFotoRelacionado(chavePaiNormalizada))
                            && valor.isTextual() && !valor.asText().isBlank()) {
                        return valor.asText();
                    }
                } else if (isCampoFotoRelacionado(chaveNormalizada)
                        && valor.isTextual() && !valor.asText().isBlank()) {
                    return valor.asText();
                }

                String encontrado = buscarCandidato(valor, profundidade + 1, novoDentroDeData, chaveNormalizada, orcamento);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode elemento : node) {
                if (orcamento[0] <= 0) {
                    return null;
                }
                String encontrado = buscarCandidato(elemento, profundidade + 1, dentroDeData, chavePaiNormalizada, orcamento);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    private boolean isCampoFotoRelacionado(String chaveNormalizada) {
        return chaveNormalizada != null && CAMPOS_FOTO_NORMALIZADOS.contains(chaveNormalizada);
    }

    /** {@code camelCase}/{@code snake_case}/{@code kebab-case} → forma canônica minúscula sem separadores. */
    private static String normalizarChave(String chave) {
        if (chave == null) {
            return "";
        }
        return chave.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private boolean detectaBase64(JsonNode node, int profundidade, int[] orcamento) {
        if (node == null || profundidade > PROFUNDIDADE_MAXIMA || orcamento[0] <= 0) {
            return false;
        }
        orcamento[0]--;

        if (node.isTextual()) {
            String texto = node.asText();
            if (texto.startsWith("data:") && texto.contains("base64,")) {
                return true;
            }
            return texto.length() >= BASE64_MIN_LENGTH && BASE64_PATTERN.matcher(texto).matches();
        }
        if (node.isObject()) {
            Iterator<JsonNode> valores = node.elements();
            while (valores.hasNext()) {
                if (orcamento[0] <= 0) {
                    return false;
                }
                if (detectaBase64(valores.next(), profundidade + 1, orcamento)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode elemento : node) {
                if (orcamento[0] <= 0) {
                    return false;
                }
                if (detectaBase64(elemento, profundidade + 1, orcamento)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Estrutura sanitizada (caminhos + tipos, nunca valores) para o diagnóstico administrativo.
     * Arrays aparecem como {@code campo[].subcampo} (sem índice). Limitada a
     * {@link #CAMINHOS_MAXIMOS_ESTRUTURA} entradas e à mesma profundidade/orçamento de nós da busca.
     */
    private List<String> extrairEstrutura(JsonNode root) {
        Set<String> caminhos = new LinkedHashSet<>();
        int[] orcamento = {NOS_MAXIMOS};
        coletarEstrutura(root, "", 0, caminhos, orcamento);
        return caminhos.stream().limit(CAMINHOS_MAXIMOS_ESTRUTURA).toList();
    }

    private void coletarEstrutura(JsonNode node, String caminho, int profundidade, Set<String> caminhos, int[] orcamento) {
        if (node == null || profundidade > PROFUNDIDADE_MAXIMA || orcamento[0] <= 0 || caminhos.size() >= CAMINHOS_MAXIMOS_ESTRUTURA) {
            return;
        }
        orcamento[0]--;

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> campos = node.fields();
            while (campos.hasNext() && orcamento[0] > 0 && caminhos.size() < CAMINHOS_MAXIMOS_ESTRUTURA) {
                Map.Entry<String, JsonNode> campo = campos.next();
                String filho = caminho.isEmpty() ? campo.getKey() : caminho + "." + campo.getKey();
                registrarCampoEstrutura(campo.getValue(), filho, profundidade + 1, caminhos, orcamento);
            }
        } else if (node.isArray()) {
            for (JsonNode elemento : node) {
                if (orcamento[0] <= 0 || caminhos.size() >= CAMINHOS_MAXIMOS_ESTRUTURA) {
                    break;
                }
                coletarEstrutura(elemento, caminho, profundidade + 1, caminhos, orcamento);
            }
        }
    }

    private void registrarCampoEstrutura(JsonNode valor, String caminho, int profundidade, Set<String> caminhos, int[] orcamento) {
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

    private String tipoDe(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isBoolean()) return "boolean";
        if (node.isNumber()) return "number";
        if (node.isNull()) return "null";
        return "unknown";
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
            boolean possuiUrlHttps, boolean possuiQueryString, boolean possuiBase64, String fotoUrl, String motivo,
            List<String> estrutura
    ) {
        return new UazapPictureEnrichmentOutcome(
                statusHttp, contentType, bodyBytes, formato, chaves,
                possuiUrlHttps, possuiQueryString, possuiBase64, fotoUrl, false, motivo, estrutura);
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
