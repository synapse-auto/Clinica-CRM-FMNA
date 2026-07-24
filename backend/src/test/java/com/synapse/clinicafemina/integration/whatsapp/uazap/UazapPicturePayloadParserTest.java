package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UazapPicturePayloadParser — parser defensivo, sem inventar contrato")
class UazapPicturePayloadParserTest {

    private UazapPicturePayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new UazapPicturePayloadParser(new ObjectMapper());
    }

    @Test
    @DisplayName("JSON com campo candidato HTTPS sem query é aceito e persistível")
    void jsonWithHttpsCandidate_isAccepted() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"picture\":\"https://cdn.example/foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/foto.jpg");
        assertThat(outcome.possuiUrlHttps()).isTrue();
        assertThat(outcome.formato()).isEqualTo("JSON");
        assertThat(outcome.chaves()).containsExactly("picture");
        assertThat(outcome.motivoNaoPersistida()).isNull();
    }

    @Test
    @DisplayName("nenhum campo candidato de foto reconhecido: não inventa URL a partir de string arbitrária")
    void noRecognizedField_doesNotInventUrl() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json",
                "{\"algumOutroCampo\":\"https://cdn.example/nao-e-foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("NENHUM_CAMPO_DE_FOTO_RECONHECIDO");
    }

    @Test
    @DisplayName("URL HTTP (não HTTPS) é rejeitada")
    void httpUrl_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"picture\":\"http://cdn.example/foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.possuiUrlHttps()).isFalse();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("esquema javascript: é rejeitado")
    void javascriptScheme_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"picture\":\"javascript:alert(1)\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("esquema data: é rejeitado")
    void dataScheme_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"picture\":\"data:image/png;base64,AAAA\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("esquema file: é rejeitado")
    void fileScheme_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"picture\":\"file:///etc/passwd\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("esquema ftp: é rejeitado")
    void ftpScheme_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"picture\":\"ftp://cdn.example/foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("URL com userinfo é rejeitada")
    void userInfoInUrl_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"picture\":\"https://user:pass@cdn.example/foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("URL_CONTEM_USERINFO");
    }

    @Test
    @DisplayName("URL com query string (possivelmente temporária/assinada) não é persistida")
    void temporaryOrSignedUrl_isNotPersisted() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json",
                "{\"picture\":\"https://cdn.example/foto.jpg?X-Amz-Signature=abc&Expires=123\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.possuiQueryString()).isTrue();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("URL_POSSIVELMENTE_TEMPORARIA_OU_ASSINADA");
    }

    @Test
    @DisplayName("resposta vazia é reconhecida sem tentar interpretar")
    void emptyResponse_isRecognized() {
        UazapPictureEnrichmentOutcome outcome = parser.parse(new UazapPictureRawResponse(200, "application/json", new byte[0]));

        assertThat(outcome.formato()).isEqualTo("VAZIO");
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("CORPO_VAZIO");
    }

    @Test
    @DisplayName("JSON inválido não gera exceção, apenas diagnóstico")
    void invalidJson_isHandledGracefully() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "isto-nao-e-json{{{");

        assertThat(outcome.formato()).isEqualTo("INVALIDO");
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("JSON_INVALIDO");
    }

    @Test
    @DisplayName("Content-Type image/* é reconhecido mas não persistido (sem estratégia de armazenamento)")
    void imageContentType_isRecognizedButNotPersisted() {
        UazapPictureEnrichmentOutcome outcome = parser.parse(
                new UazapPictureRawResponse(200, "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));

        assertThat(outcome.formato()).isEqualTo("IMAGEM");
        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("RESPOSTA_BINARIA_SEM_ESTRATEGIA_DE_ARMAZENAMENTO");
    }

    @Test
    @DisplayName("status HTTP de erro é reportado sem tentar extrair foto")
    void errorStatus_isReportedWithoutExtractingPhoto() {
        UazapPictureEnrichmentOutcome outcome = parser.parse(
                new UazapPictureRawResponse(404, "application/json", "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.formato()).isEqualTo("ERRO_HTTP");
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("STATUS_HTTP_404");
        assertThat(outcome.fotoUrl()).isNull();
    }

    @Test
    @DisplayName("string longa em base64 é detectada e reportada, mesmo sem ser usada como URL")
    void base64String_isDetected() {
        String base64Longo = "A".repeat(250);
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json",
                "{\"data\":\"" + base64Longo + "\"}");

        assertThat(outcome.possuiBase64()).isTrue();
        assertThat(outcome.fotoUrl()).isNull();
    }

    @Test
    @DisplayName("campo candidato aninhado em objeto com sub-chave url é aceito")
    void nestedCandidateObject_isAccepted() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json",
                "{\"picture\":{\"url\":\"https://cdn.example/nested.jpg\",\"type\":\"image\"}}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/nested.jpg");
    }

    // ── Correção: payload real aninhado ({"status": ..., "data": ...}) ─────────────────────────

    @Test
    @DisplayName("campo de foto dentro de 'data' (evidência real de produção) é encontrado recursivamente")
    void fieldInsideData_isFoundRecursively() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json;charset=utf-8",
                "{\"status\":\"success\",\"data\":{\"picture\":\"https://cdn.example/real.jpg\"}}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/real.jpg");
        assertThat(outcome.motivoNaoPersistida()).isNull();
    }

    @Test
    @DisplayName("objeto de foto com sub-chave 'url' interna dentro de 'data' é aceito")
    void photoObjectWithInnerUrl_insideData_isAccepted() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"status\":\"success\",\"data\":{\"profilePicture\":{\"url\":\"https://cdn.example/inner.jpg\"}}}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/inner.jpg");
    }

    @Test
    @DisplayName("array dentro de 'data' é percorrido recursivamente")
    void arrayInsideData_isTraversedRecursively() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"status\":\"success\",\"data\":[{\"photoUrl\":\"https://cdn.example/array-item.jpg\"}]}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/array-item.jpg");
    }

    @Test
    @DisplayName("múltiplos níveis aninhados (dentro do limite de profundidade) são percorridos")
    void deeplyNestedWithinLimit_isFound() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"data\":{\"result\":{\"contact\":{\"profile\":{\"picture\":\"https://cdn.example/deep.jpg\"}}}}}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/deep.jpg");
    }

    @Test
    @DisplayName("nomes snake_case são reconhecidos por comparação defensiva")
    void snakeCaseFieldNames_areRecognized() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"data\":{\"profile_picture_url\":\"https://cdn.example/snake.jpg\"}}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/snake.jpg");
    }

    @Test
    @DisplayName("comparação de nome de campo é case-insensitive")
    void fieldNameComparison_isCaseInsensitive() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"data\":{\"PICTURE\":\"https://cdn.example/upper.jpg\"}}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/upper.jpg");
    }

    @Test
    @DisplayName("chave genérica 'url' solta na raiz (sem envelope 'data' nem pai relacionado a foto) é ignorada")
    void bareTopLevelUrl_withoutDataOrPhotoParent_isIgnored() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"url\":\"https://api.uazapi.com/docs\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("NENHUM_CAMPO_DE_FOTO_RECONHECIDO");
    }

    @Test
    @DisplayName("URL genérica solta ('url' fora de data/foto) é ignorada; a real dentro de data é usada")
    void genericUnrelatedUrl_isIgnored() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"url\":\"https://api.uazapi.com/docs\",\"data\":{\"picture\":\"https://cdn.example/real.jpg\"}}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/real.jpg");
    }

    @Test
    @DisplayName("campo além do limite de profundidade não é encontrado")
    void beyondDepthLimit_isNotFound() {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            json.append("{\"wrap\":");
        }
        json.append("{\"picture\":\"https://cdn.example/too-deep.jpg\"}");
        json.append("}".repeat(10));

        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json", json.toString());

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("NENHUM_CAMPO_DE_FOTO_RECONHECIDO");
    }

    @Test
    @DisplayName("payload muito 'largo' (excede o orçamento de nós) não encontra o campo real ao final")
    void beyondNodeBudget_isNotFound() {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < 1000; i++) {
            json.append("\"campo").append(i).append("\":\"valor-irrelevante\",");
        }
        json.append("\"picture\":\"https://cdn.example/too-far.jpg\"}");

        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json", json.toString());

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("NENHUM_CAMPO_DE_FOTO_RECONHECIDO");
    }

    @Test
    @DisplayName("base64 aninhado dentro de 'data' é detectado")
    void nestedBase64_isDetected() {
        String base64Longo = "A".repeat(250);
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"data\":{\"thumbnail\":\"" + base64Longo + "\"}}");

        assertThat(outcome.possuiBase64()).isTrue();
    }

    @Test
    @DisplayName("estrutura sanitizada reporta caminhos+tipos, nunca valores, URLs, telefone ou nome")
    void sanitizedStructure_containsOnlyPathsAndTypes_neverValues() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json;charset=utf-8",
                "{\"status\":\"success\",\"data\":{\"picture\":\"https://cdn.example/real.jpg\","
                        + "\"telefone\":\"5511999990000\",\"nome\":\"Paciente Teste\"}}");

        assertThat(outcome.estrutura()).contains("status:string", "data:object", "data.picture:string");
        String estruturaConcatenada = String.join(",", outcome.estrutura());
        assertThat(estruturaConcatenada).doesNotContain("https://cdn.example/real.jpg");
        assertThat(estruturaConcatenada).doesNotContain("5511999990000");
        assertThat(estruturaConcatenada).doesNotContain("Paciente Teste");
    }

    @Test
    @DisplayName("estrutura sanitizada representa arrays como campo[].subcampo")
    void sanitizedStructure_representsArraysAsFieldBracket() {
        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json",
                "{\"data\":[{\"photoUrl\":\"https://cdn.example/array-item.jpg\"}]}");

        assertThat(outcome.estrutura()).contains("data:array", "data[].photoUrl:string");
    }

    @Test
    @DisplayName("estrutura sanitizada é limitada a no máximo 100 caminhos")
    void sanitizedStructure_isCappedAt100Paths() {
        StringBuilder json = new StringBuilder("{\"data\":{");
        for (int i = 0; i < 150; i++) {
            json.append("\"campo").append(i).append("\":\"valor").append(i).append("\"");
            if (i < 149) json.append(",");
        }
        json.append("}}");

        UazapPictureEnrichmentOutcome outcome = parse(201, "application/json", json.toString());

        assertThat(outcome.estrutura()).hasSizeLessThanOrEqualTo(100);
    }

    private UazapPictureEnrichmentOutcome parse(int status, String contentType, String body) {
        return parser.parse(new UazapPictureRawResponse(status, contentType, body.getBytes(StandardCharsets.UTF_8)));
    }
}
