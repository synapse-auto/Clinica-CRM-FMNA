package com.synapse.clinicafemina.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_BYTES = 72;
    public static final String MESSAGE = "A senha deve ter pelo menos 6 caracteres, incluindo uma letra e um número, e no máximo 72 bytes. Caracteres especiais são permitidos.";

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        return password != null
                && password.codePointCount(0, password.length()) >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && password.codePoints().anyMatch(Character::isLetter)
                && password.codePoints().anyMatch(Character::isDigit);
    }
}
