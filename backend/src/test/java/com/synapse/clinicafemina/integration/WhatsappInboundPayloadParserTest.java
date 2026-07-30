package com.synapse.clinicafemina.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsappInboundPayloadParserTest {

    private final WhatsappInboundPayloadParser parser = new WhatsappInboundPayloadParser();

    /** Contagem por code points, coerente com {@code VARCHAR(60)} do PostgreSQL. */
    private static int codePoints(String s) {
        return s.codePointCount(0, s.length());
    }

    /** Verdadeiro se a string não possui nenhum surrogate solto (par UTF-16 partido ao meio). */
    private static boolean possuiSurrogateSolto(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return true;
                }
                i++; // par completo, pula o low surrogate
            } else if (Character.isLowSurrogate(c)) {
                return true; // low surrogate sem high antes
            }
        }
        return false;
    }

    @Test
    void should_extract_long_text_body_without_truncation() {
        String textoLongo = """
                nasci em 28/01/1999 cpf 00000000000 telefone 5500000000000
                quero ultrassonografia transvaginal com observacoes adicionais
                """.strip();

        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-long",
                "type", "text",
                "text", Map.of("body", textoLongo)
        ));

        assertEquals("TEXTO", dados.tipoMedia());
        assertEquals(textoLongo, dados.conteudo());
    }

    @Test
    void should_keep_preview_within_database_column_size() {
        String textoLongo = "a".repeat(90);

        String previa = parser.limitarPrevia(textoLongo);

        assertEquals(60, previa.length());
        assertTrue(previa.endsWith("..."));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 59, 60})
    @DisplayName("até 60 code points a prévia é mantida sem alteração")
    void preview_upTo60CodePoints_isKeptUnchanged(int tamanho) {
        String texto = "a".repeat(tamanho);

        String previa = parser.limitarPrevia(texto);

        assertEquals(texto, previa);
    }

    @ParameterizedTest
    @ValueSource(ints = {61, 100, 500, 2000})
    @DisplayName("acima de 60 code points a prévia é limitada, cabe na coluna e termina com sufixo")
    void preview_above60CodePoints_isLimitedWithinColumn(int tamanho) {
        String texto = "a".repeat(tamanho);

        String previa = parser.limitarPrevia(texto);

        assertTrue(codePoints(previa) <= 60, "prévia deve caber em VARCHAR(60) por code points");
        assertTrue(previa.length() <= 60, "prévia deve caber em VARCHAR(60) por unidades UTF-16");
        assertTrue(previa.endsWith("..."));
        assertFalse(possuiSurrogateSolto(previa));
    }

    @Test
    @DisplayName("prévia com acentos preserva os caracteres e respeita o limite")
    void preview_withAccents_isValidAndWithinLimit() {
        String texto = "Avaliação pré-natal em São João com acompanhamento nutricional detalhado e observações";

        String previa = parser.limitarPrevia(texto);

        assertTrue(codePoints(previa) <= 60);
        assertFalse(possuiSurrogateSolto(previa));
        assertTrue(previa.startsWith("Avaliação"));
    }

    @Test
    @DisplayName("prévia de mensagem só com emojis nunca parte um par surrogate")
    void preview_allEmoji_neverSplitsSurrogatePair() {
        String texto = "😀".repeat(1000); // 😀 x1000 = 1000 code points, 2000 unidades UTF-16

        String previa = parser.limitarPrevia(texto);

        assertFalse(possuiSurrogateSolto(previa), "nunca pode restar surrogate solto");
        assertTrue(codePoints(previa) <= 60);
        assertTrue(previa.endsWith("..."));
    }

    @Test
    @DisplayName("emoji exatamente na região do corte não vira surrogate solto (regressão do bug)")
    void preview_emojiExactlyAtCut_isNotBroken() {
        // 56 'a' + emojis: um substring(0,57) ingênuo cortaria o 1º emoji ao meio (surrogate solto).
        String texto = "a".repeat(56) + "😀".repeat(10);

        String previa = parser.limitarPrevia(texto);

        assertFalse(possuiSurrogateSolto(previa), "corte não pode partir o emoji ao meio");
        assertTrue(codePoints(previa) <= 60);
    }

    @Test
    @DisplayName("prévia com números longos e conteúdo tipo CPF/telefone fictícios permanece válida")
    void preview_withLongNumbersAndFakeIdentifiers_isValid() {
        String texto = "Documento 12345678901234567890 telefone 5599999999999 cpf 000.000.000-00 repetido varias vezes";

        String previa = parser.limitarPrevia(texto);

        assertTrue(codePoints(previa) <= 60);
        assertFalse(possuiSurrogateSolto(previa));
        assertTrue(previa.endsWith("..."));
    }

    @Test
    @DisplayName("conteúdo integral nunca é truncado pela limitação da prévia")
    void content_isNeverTruncatedByPreviewLimiting() {
        String texto = "linha 1\nlinha 2 " + "conteudo ".repeat(300) + "😀 fim";

        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-full",
                "type", "text",
                "text", Map.of("body", texto)
        ));
        String previa = parser.limitarPrevia(dados.conteudo());

        assertEquals(texto, dados.conteudo(), "conteúdo deve permanecer íntegro");
        assertTrue(codePoints(previa) <= 60, "somente a prévia é limitada");
    }

    @Test
    void should_preserve_newlines_and_long_numbers_in_text_body() {
        String texto = "linha 1\nlinha 2 com numero 12345678901234567890";

        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-lines",
                "type", "text",
                "text", Map.of("body", texto)
        ));

        assertEquals(texto, dados.conteudo());
    }

    @Test
    @DisplayName("Meta e UAZAP preservam Unicode completo no envelope inbound comum")
    void should_preserve_complete_unicode_from_meta_and_uazap_payloads() {
        String unicode = "\uD83D\uDE02 \u2764\uFE0F \uD83D\uDC4D\uD83C\uDFFD \uD83E\uDEC6 "
                + "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66 "
                + "\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08 \uD83C\uDDE7\uD83C\uDDF7 1\uFE0F\u20E3 \u263A\uFE0F";

        for (String provider : new String[]{"Meta", "UAZAP"}) {
            WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                    "id", provider + "-unicode",
                    "type", "text",
                    "text", Map.of("body", unicode)
            ));

            assertEquals(unicode, dados.conteudo());
            assertFalse(dados.conteudo().contains("\uFFFD"));
        }
    }

    @Test
    void should_classify_sticker_as_image_and_ignore_generic_filename() {
        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-sticker",
                "type", "sticker",
                "sticker", Map.of("id", "media-sticker", "mime_type", "image/webp", "filename", "outro")
        ));

        assertEquals("IMAGEM", dados.tipoMedia());
        assertEquals("image/webp", dados.mimeType());
        assertEquals("figurinha.webp", dados.nomeArquivo());
        assertEquals("media-sticker", dados.mediaId());
    }

    @Test
    void should_render_reaction_as_text_without_creating_fake_media() {
        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-reaction",
                "type", "reaction",
                "reaction", Map.of("emoji", "\u2764\uFE0F", "message_id", "wamid-original")
        ));

        assertEquals("TEXTO", dados.tipoMedia());
        assertEquals("Paciente reagiu com \u2764\uFE0F", dados.conteudo());
        assertEquals(null, dados.mediaId());
    }

    @Test
    void should_render_reaction_removal_as_discrete_text_without_an_empty_message() {
        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-reaction-removed",
                "type", "reaction",
                "reaction", Map.of("message_id", "wamid-original")
        ));

        assertEquals("TEXTO", dados.tipoMedia());
        assertEquals("Paciente removeu uma reação", dados.conteudo());
        assertEquals(null, dados.mediaId());
    }

    @Test
    void should_use_button_text_instead_of_technical_payload() {
        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-button",
                "type", "button",
                "button", Map.of("text", "Sim, podemos", "payload", "template_yes_42")
        ));

        assertEquals("TEXTO", dados.tipoMedia());
        assertEquals("Sim, podemos", dados.conteudo());
        assertEquals("Sim, podemos", parser.limitarPrevia(dados.conteudo()));
    }

    @Test
    void should_use_meta_interactive_button_reply_title() {
        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-interactive-button",
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button_reply",
                        "button_reply", Map.of("id", "technical-yes", "title", "Sim, podemos")
                )
        ));

        assertEquals("TEXTO", dados.tipoMedia());
        assertEquals("Sim, podemos", dados.conteudo());
    }

    @Test
    void should_preserve_meta_interactive_list_title_and_description() {
        WhatsappInboundPayloadParser.DadosMensagem dados = parser.extrairDados(Map.of(
                "id", "wamid-interactive-list",
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list_reply",
                        "list_reply", Map.of("id", "technical-list", "title", "Consulta pré-natal", "description", "Quinta às 14h")
                )
        ));

        assertEquals("TEXTO", dados.tipoMedia());
        assertEquals("Consulta pré-natal\nQuinta às 14h", dados.conteudo());
    }

    @Test
    void should_use_friendly_text_for_non_supported_inbound_types() {
        assertEquals("Localização recebida", parser.extrairDados(Map.of("type", "location")).conteudo());
        assertEquals("Contato compartilhado", parser.extrairDados(Map.of("type", "contacts")).conteudo());
        assertEquals("Tipo de mensagem ainda não suportado", parser.extrairDados(Map.of("type", "unknown")).conteudo());
    }
}
