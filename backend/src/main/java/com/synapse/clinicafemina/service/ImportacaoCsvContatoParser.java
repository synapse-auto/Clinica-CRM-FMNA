package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.exception.BadRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ImportacaoCsvContatoParser {

    public static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 50_000;
    public static final int MAX_COLUMNS = 100;

    public ArquivoCsvLido parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("O arquivo está vazio.");
        }
        try {
            return parse(file.getBytes(), file.getOriginalFilename());
        } catch (IOException exception) {
            throw new BadRequestException("Não foi possível ler o arquivo CSV.");
        }
    }

    ArquivoCsvLido parse(byte[] bytes, String originalFilename) {
        String fileName = sanitizeFilename(originalFilename);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("Selecione um arquivo CSV.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("O arquivo está vazio.");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new BadRequestException("O arquivo excede o limite de 5 MB.");
        }
        for (byte value : bytes) {
            if (value == 0) {
                throw new BadRequestException("O arquivo não contém um CSV textual válido.");
            }
        }
        Decodificado decoded = decode(bytes);
        if (decoded.text().isBlank()) {
            throw new BadRequestException("O arquivo está vazio.");
        }
        validarTexto(decoded.text());
        char delimiter = detectarDelimitador(decoded.text());
        List<CSVRecord> records = readRecords(decoded.text(), delimiter);
        if (records.isEmpty()) {
            throw new BadRequestException("O arquivo está vazio.");
        }
        List<String> rawHeaders = copyValues(records.getFirst());
        Set<Integer> emptyColumnsWithoutData = findEmptyColumnsWithoutData(rawHeaders, records);
        List<String> headers = removeColumns(rawHeaders, emptyColumnsWithoutData);
        validarHeaders(headers);
        List<LinhaCsv> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            if (rows.size() >= MAX_DATA_ROWS) {
                throw new BadRequestException("O arquivo possui mais de 50.000 contatos.");
            }
            rows.add(new LinhaCsv(index + 1, removeColumns(copyValues(records.get(index)), emptyColumnsWithoutData)));
        }
        return new ArquivoCsvLido(
                sha256(bytes),
                fileName,
                decoded.encoding(),
                delimiter,
                List.copyOf(headers),
                List.copyOf(rows)
        );
    }

    private List<CSVRecord> readRecords(String text, char delimiter) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setIgnoreEmptyLines(false)
                .setIgnoreSurroundingSpaces(true)
                .build();
        try (CSVParser parser = CSVParser.parse(text, format)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.stream().anyMatch(record -> record.size() > MAX_COLUMNS)) {
                throw new BadRequestException("O arquivo possui mais de 100 colunas.");
            }
            return records;
        } catch (IOException | IllegalArgumentException exception) {
            throw new BadRequestException("O arquivo não contém um CSV válido.");
        }
    }

    private void validarHeaders(List<String> headers) {
        if (headers.isEmpty()) {
            throw new BadRequestException("O arquivo está vazio.");
        }
        if (headers.size() > MAX_COLUMNS) {
            throw new BadRequestException("O arquivo possui mais de 100 colunas.");
        }
        Set<String> seen = new HashSet<>();
        for (String header : headers) {
            String normalized = normalizarHeader(header);
            if (normalized.isBlank()) {
                throw new BadRequestException("O CSV possui um cabeçalho vazio.");
            }
            if (!seen.add(normalized)) {
                throw new BadRequestException("O CSV possui cabeçalhos duplicados.");
            }
        }
    }

    /**
     * Exportações de planilha com vírgula/ponto e vírgula final costumam criar uma coluna sem
     * cabeçalho e sem nenhum valor. Ela não representa dado de contato e pode ser removida com
     * segurança. Uma coluna sem cabeçalho que contenha qualquer valor continua sendo rejeitada.
     */
    private Set<Integer> findEmptyColumnsWithoutData(List<String> headers, List<CSVRecord> records) {
        Set<Integer> emptyColumns = new HashSet<>();
        for (int index = 0; index < headers.size(); index++) {
            if (!normalizarHeader(headers.get(index)).isBlank()) {
                continue;
            }
            int columnIndex = index;
            boolean hasValue = records.stream()
                    .skip(1)
                    .anyMatch(record -> columnIndex < record.size() && !record.get(columnIndex).isBlank());
            if (!hasValue) {
                emptyColumns.add(index);
            }
        }
        return emptyColumns;
    }

    private List<String> removeColumns(List<String> values, Set<Integer> indicesToRemove) {
        if (indicesToRemove.isEmpty()) {
            return values;
        }
        List<String> filtered = new ArrayList<>(Math.max(0, values.size() - indicesToRemove.size()));
        for (int index = 0; index < values.size(); index++) {
            if (!indicesToRemove.contains(index)) {
                filtered.add(values.get(index));
            }
        }
        return filtered;
    }

    static String normalizarHeader(String value) {
        String withoutAccents = java.text.Normalizer.normalize(
                value == null ? "" : value, java.text.Normalizer.Form.NFD
        ).replaceAll("\\p{M}", "");
        return withoutAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private char detectarDelimitador(String text) {
        int commas = 0;
        int semicolons = 0;
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') {
                boolean escaped = quoted && index + 1 < text.length() && text.charAt(index + 1) == '"';
                if (escaped) {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && current == ',') {
                commas++;
            } else if (!quoted && current == ';') {
                semicolons++;
            } else if (!quoted && (current == '\n' || current == '\r') && (commas > 0 || semicolons > 0)) {
                break;
            }
        }
        return semicolons >= commas ? ';' : ',';
    }

    private Decodificado decode(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
            return new Decodificado(text, "UTF-8");
        } catch (CharacterCodingException exception) {
            return new Decodificado(new String(bytes, Charset.forName("windows-1252")), "Windows-1252");
        }
    }

    private void validarTexto(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t') {
                throw new BadRequestException("O arquivo não contém um CSV textual válido.");
            }
        }
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            try (Formatter formatter = new Formatter()) {
                for (byte value : digest) {
                    formatter.format("%02x", value);
                }
                return formatter.toString();
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private List<String> copyValues(CSVRecord record) {
        List<String> values = new ArrayList<>();
        record.forEach(values::add);
        return values;
    }

    private String sanitizeFilename(String value) {
        String filename = value == null ? "contatos.csv" : value.replaceAll("[\\r\\n\\\\/]", "_").trim();
        return filename.isBlank() ? "contatos.csv" : filename.substring(0, Math.min(filename.length(), 120));
    }

    private record Decodificado(String text, String encoding) {
    }

    public record ArquivoCsvLido(
            String hash,
            String fileName,
            String encoding,
            char delimiter,
            List<String> headers,
            List<LinhaCsv> rows
    ) {
    }

    public record LinhaCsv(int rowNumber, List<String> values) {
    }
}
