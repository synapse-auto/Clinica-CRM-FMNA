package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.exception.BadRequestException;

public final class WhatsappPhoneNormalizer {

    private static final String BRAZIL_COUNTRY_CODE = "55";

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

    private static String validateE164(String digits) {
        if (digits.length() < 8 || digits.length() > 15 || digits.startsWith("0")) {
            throw new BadRequestException("Telefone invalido. Informe um numero internacional valido.");
        }
        return digits;
    }
}
