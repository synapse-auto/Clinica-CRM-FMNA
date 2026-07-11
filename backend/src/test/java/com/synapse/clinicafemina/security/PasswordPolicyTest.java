package com.synapse.clinicafemina.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void should_accept_passwords_with_letters_numbers_and_optional_special_characters() {
        assertTrue(PasswordPolicy.isStrong("abc123"));
        assertTrue(PasswordPolicy.isStrong("Senha@123"));
        assertTrue(PasswordPolicy.isStrong("Ultra#2026"));
        assertTrue(PasswordPolicy.isStrong("Acesso!123"));
        assertTrue(PasswordPolicy.isStrong("Minha_Senha9"));
        assertTrue(PasswordPolicy.isStrong(" Senha1 "));
    }

    @Test
    void should_reject_password_without_letter_number_or_minimum_length() {
        assertFalse(PasswordPolicy.isStrong("123456"));
        assertFalse(PasswordPolicy.isStrong("abcdef"));
        assertFalse(PasswordPolicy.isStrong("ab12"));
        assertFalse(PasswordPolicy.isStrong(null));
    }

    @Test
    void should_reject_password_over_bcrypt_byte_limit() {
        assertTrue(PasswordPolicy.isStrong("a1" + "x".repeat(70)));
        assertFalse(PasswordPolicy.isStrong("a1" + "x".repeat(71)));
        assertFalse(PasswordPolicy.isStrong("a1" + "á".repeat(36)));
    }
}
