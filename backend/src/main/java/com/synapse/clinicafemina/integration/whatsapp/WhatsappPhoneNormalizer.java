package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.exception.BadRequestException;

import java.util.LinkedHashSet;
import java.util.Set;

public final class WhatsappPhoneNormalizer {

    private static final String BRAZIL_COUNTRY_CODE = "55";
    private static final Set<String> BRAZILIAN_AREA_CODES = Set.of(
            "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "21", "22", "24", "27", "28",
            "31", "32", "33", "34", "35", "37", "38",
            "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "53", "54", "55",
            "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "73", "74", "75", "77", "79",
            "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99"
    );

    private WhatsappPhoneNormalizer() {
    }

    public static String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new BadRequestException("Telefone e obrigatorio.");
        }
        String trimmed = rawPhone.trim();
        boolean explicitCountryCode = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");
        if (explicitCountryCode) {
            return validateE164(digits);
        }
        if (digits.length() == 10 || digits.length() == 11) {
            return BRAZIL_COUNTRY_CODE + digits;
        }
        if (digits.startsWith(BRAZIL_COUNTRY_CODE)
                && (digits.length() == 12 || digits.length() == 13)) {
            return digits;
        }
        throw new BadRequestException("Telefone invalido. Informe DDD e numero.");
    }

    /**
     * Produz somente aliases brasileiros estruturalmente seguros, preservando primeiro o valor
     * informado. Números internacionais, fixos e formatos brasileiros ambíguos permanecem sem
     * alias.
     */
    public static Set<String> safeAliases(String rawPhone) {
        String normalized = normalize(rawPhone);
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(normalized);
        if (!isBrazilianMobileCandidate(normalized)) {
            return Set.copyOf(aliases);
        }
        if (normalized.length() == 13) {
            aliases.add(normalized.substring(0, 4) + normalized.substring(5));
        } else {
            aliases.add(normalized.substring(0, 4) + "9" + normalized.substring(4));
        }
        return java.util.Collections.unmodifiableSet(aliases);
    }

    private static boolean isBrazilianMobileCandidate(String phone) {
        if (!phone.startsWith(BRAZIL_COUNTRY_CODE)
                || (phone.length() != 12 && phone.length() != 13)
                || !BRAZILIAN_AREA_CODES.contains(phone.substring(2, 4))) {
            return false;
        }
        if (phone.length() == 13) {
            return phone.charAt(4) == '9' && isLegacyMobilePrefix(phone.charAt(5));
        }
        return isLegacyMobilePrefix(phone.charAt(4));
    }

    private static boolean isLegacyMobilePrefix(char digit) {
        return digit >= '6' && digit <= '9';
    }

    private static String validateE164(String digits) {
        if (digits.length() < 8 || digits.length() > 15 || digits.startsWith("0")) {
            throw new BadRequestException("Telefone invalido. Informe um numero internacional valido.");
        }
        return digits;
    }
}
