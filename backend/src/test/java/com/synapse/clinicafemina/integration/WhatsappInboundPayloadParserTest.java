package com.synapse.clinicafemina.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsappInboundPayloadParserTest {

    private final WhatsappInboundPayloadParser parser = new WhatsappInboundPayloadParser();

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
}
