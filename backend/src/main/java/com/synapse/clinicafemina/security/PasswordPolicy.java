package com.synapse.clinicafemina.security;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 72;
    public static final String MESSAGE = "A senha deve ter no mínimo 6 caracteres, contendo letras e números.";
    private static final String CRM_PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$";

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        return password != null
                && password.length() <= MAX_LENGTH
                && password.matches(CRM_PASSWORD_PATTERN);
    }
}
