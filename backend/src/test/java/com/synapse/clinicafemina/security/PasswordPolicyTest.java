package com.synapse.clinicafemina.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void should_accept_crm_password_with_letters_and_numbers_only() {
        assertTrue(PasswordPolicy.isStrong("abc123"));
        assertTrue(PasswordPolicy.isStrong("Lucas123"));
        assertTrue(PasswordPolicy.isStrong("Atendente1"));
        assertTrue(PasswordPolicy.isStrong("Gestor2026"));
        assertTrue(PasswordPolicy.isStrong("Ultra123"));
    }

    @Test
    void should_reject_password_without_required_crm_pattern() {
        assertFalse(PasswordPolicy.isStrong("123456"));
        assertFalse(PasswordPolicy.isStrong("abcdef"));
        assertFalse(PasswordPolicy.isStrong("abc@123"));
        assertFalse(PasswordPolicy.isStrong("ab12"));
    }
}
