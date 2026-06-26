package com.synapse.clinicafemina.security;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        return password != null && password.length() >= MIN_LENGTH;
    }
}
