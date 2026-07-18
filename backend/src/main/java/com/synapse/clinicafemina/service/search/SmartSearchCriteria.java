package com.synapse.clinicafemina.service.search;

import com.synapse.clinicafemina.exception.BadRequestException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record SmartSearchCriteria(
        int mode,
        String normalized,
        String externalExact,
        String digits,
        String localPhoneDigits,
        String phoneWithCountryCode,
        Long exactId,
        List<String> tokens
) {

    public static final int MODE_NONE = 0;
    public static final int MODE_TEXT = 1;
    public static final int MODE_NUMERIC = 2;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_TOKENS = 5;

    public static SmartSearchCriteria from(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("A pesquisa deve ter no maximo 100 caracteres");
        }

        String normalized = normalize(value);
        String digits = value.replaceAll("\\D", "");
        boolean numeric = !digits.isEmpty() && value.replaceAll("[\\d\\s+()./-]", "").isBlank();
        int mode = normalized.isBlank()
                ? MODE_NONE
                : numeric ? MODE_NUMERIC : normalized.length() >= 2 ? MODE_TEXT : MODE_NONE;
        List<String> tokens = mode == MODE_TEXT
                ? Arrays.stream(normalized.split(" "))
                .filter(token -> !token.isBlank())
                .distinct()
                .limit(MAX_TOKENS)
                .toList()
                : List.of();
        String localPhone = digits.startsWith("55") && digits.length() == 13
                ? digits.substring(2)
                : digits;
        String withCountryCode = localPhone.length() == 11 ? "55" + localPhone : digits;
        return new SmartSearchCriteria(
                mode,
                normalized,
                value.toUpperCase(Locale.ROOT),
                digits,
                localPhone,
                withCountryCode,
                parseId(numeric ? digits : ""),
                tokens
        );
    }

    public String token(int index) {
        return index < tokens.size() ? tokens.get(index) : "";
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static Long parseId(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
