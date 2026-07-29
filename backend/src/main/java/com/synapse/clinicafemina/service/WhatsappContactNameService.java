package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class WhatsappContactNameService {

    public static final String PLACEHOLDER = "Contato WhatsApp";
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 120;

    public String sanitizeRequired(String rawName, String normalizedPhone) {
        if (rawName == null) {
            throw new BadRequestException("Nome do contato e obrigatorio.");
        }
        String sanitized = rawName
                .replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        int length = sanitized.codePointCount(0, sanitized.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH
                || "null".equalsIgnoreCase(sanitized)
                || sanitized.codePoints().noneMatch(Character::isLetter)
                || isPhoneName(sanitized, normalizedPhone)) {
            throw new BadRequestException(
                    "Nome do contato deve ter entre 2 e 120 caracteres e conter letras."
            );
        }
        return sanitized;
    }

    public String toSearchName(String name) {
        return name.toUpperCase(Locale.ROOT);
    }

    public boolean isPlaceholder(String currentName, String normalizedPhone) {
        if (currentName == null || currentName.isBlank()) {
            return true;
        }
        String normalizedName = currentName.trim();
        return PLACEHOLDER.equalsIgnoreCase(normalizedName)
                || "null".equalsIgnoreCase(normalizedName)
                || isPhoneName(normalizedName, normalizedPhone);
    }

    private boolean isPhoneName(String name, String normalizedPhone) {
        if (normalizedPhone == null) {
            return false;
        }
        String nameDigits = name.replaceAll("\\D", "");
        return !nameDigits.isEmpty()
                && name.codePoints().noneMatch(Character::isLetter)
                && nameDigits.equals(normalizedPhone);
    }
}
