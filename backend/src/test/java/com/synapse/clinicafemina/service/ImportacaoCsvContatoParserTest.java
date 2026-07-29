package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportacaoCsvContatoParserTest {

    private final ImportacaoCsvContatoParser parser = new ImportacaoCsvContatoParser();

    @Test
    void should_parse_semicolon_csv_with_utf8_bom_and_crlf() {
        byte[] bytes = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "Nome;Telefone\r\nMaria da Silva;5583999999999\r\n".getBytes());

        var result = parser.parse(bytes, "contatos.csv");

        assertEquals("UTF-8", result.encoding());
        assertEquals(';', result.delimiter());
        assertEquals(java.util.List.of("Nome", "Telefone"), result.headers());
        assertEquals(2, result.rows().getFirst().rowNumber());
    }

    @Test
    void should_parse_comma_csv_with_quoted_delimiters() {
        var result = parser.parse("nome,telefone,observacao\n\"Maria, da Silva\",5583999999999,\"usa; retorno\"\n"
                .getBytes(), "contatos.csv");

        assertEquals(',', result.delimiter());
        assertEquals("Maria, da Silva", result.rows().getFirst().values().getFirst());
        assertEquals("usa; retorno", result.rows().getFirst().values().get(2));
    }

    @Test
    void should_decode_windows_1252_when_utf8_is_invalid() {
        var result = parser.parse("nome;telefone\nJoão;5583999999999\n".getBytes(Charset.forName("windows-1252")), "contatos.csv");

        assertEquals("Windows-1252", result.encoding());
        assertEquals("João", result.rows().getFirst().values().getFirst());
    }

    @Test
    void should_reject_empty_binary_duplicate_headers_and_excessive_rows() {
        assertThrows(BadRequestException.class, () -> parser.parse(new byte[0], "contatos.csv"));
        assertThrows(BadRequestException.class, () -> parser.parse("nome;telefone".getBytes(), "contatos.xlsx"));
        assertThrows(BadRequestException.class, () -> parser.parse(new byte[] {'n', 0, 'm'}, "contatos.csv"));
        assertThrows(BadRequestException.class, () -> parser.parse("Nome; nome \nA;1\n".getBytes(), "contatos.csv"));
        String csv = "nome;telefone\n" + "Ana;5583999999999\n".repeat(10_001);
        assertThrows(BadRequestException.class, () -> parser.parse(csv.getBytes(), "contatos.csv"));
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
