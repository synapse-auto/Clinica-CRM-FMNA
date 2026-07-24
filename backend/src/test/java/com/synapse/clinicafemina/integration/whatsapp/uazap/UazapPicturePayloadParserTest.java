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
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"url\":\"https://cdn.example/foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isEqualTo("https://cdn.example/foto.jpg");
        assertThat(outcome.possuiUrlHttps()).isTrue();
        assertThat(outcome.formato()).isEqualTo("JSON");
        assertThat(outcome.chaves()).containsExactly("url");
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
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"url\":\"javascript:alert(1)\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("esquema data: é rejeitado")
    void dataScheme_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"url\":\"data:image/png;base64,AAAA\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("esquema file: é rejeitado")
    void fileScheme_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"url\":\"file:///etc/passwd\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("esquema ftp: é rejeitado")
    void ftpScheme_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"url\":\"ftp://cdn.example/foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("ESQUEMA_NAO_HTTPS");
    }

    @Test
    @DisplayName("URL com userinfo é rejeitada")
    void userInfoInUrl_isRejected() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json", "{\"url\":\"https://user:pass@cdn.example/foto.jpg\"}");

        assertThat(outcome.fotoUrl()).isNull();
        assertThat(outcome.motivoNaoPersistida()).isEqualTo("URL_CONTEM_USERINFO");
    }

    @Test
    @DisplayName("URL com query string (possivelmente temporária/assinada) não é persistida")
    void temporaryOrSignedUrl_isNotPersisted() {
        UazapPictureEnrichmentOutcome outcome = parse(200, "application/json",
                "{\"url\":\"https://cdn.example/foto.jpg?X-Amz-Signature=abc&Expires=123\"}");

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

    private UazapPictureEnrichmentOutcome parse(int status, String contentType, String body) {
        return parser.parse(new UazapPictureRawResponse(status, contentType, body.getBytes(StandardCharsets.UTF_8)));
    }
}
