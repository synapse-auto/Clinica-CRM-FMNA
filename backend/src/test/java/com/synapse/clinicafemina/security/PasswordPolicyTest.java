package com.synapse.clinicafemina.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {
            "Senha@123",
            "Ultra#2026",
            "Teste!2026&",
            "Senha$Com%Simbolos9",
            "Senha+Com=Simbolos7",
            "Senha.Com:DoisPontos8"
    })
    @org.junit.jupiter.api.DisplayName("FMNA: conjunto de senhas com caracteres especiais diversos é aceito, preservado exatamente")
    void should_accept_fmna_special_character_password_set(String senha) {
        assertTrue(PasswordPolicy.isStrong(senha), "esperado válido: " + senha);
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

    @ParameterizedTest
    @ValueSource(strings = {
            "Senha@123", "Ultra#2026", "Teste!2026&",
            "Senha$Com%Simbolos9", "Senha+Com=Simbolos7", "Senha.Com:DoisPontos8",
            "Acentuação9Válida"
    })
    @org.junit.jupiter.api.DisplayName("BCrypt real (cost 12): encode/matches roda-trip preserva a senha exata, incluindo símbolos e acentos")
    void should_round_trip_through_real_bcrypt_preserving_special_characters(String senha) {
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);
        String hash = encoder.encode(senha);
        assertTrue(encoder.matches(senha, hash), "hash deveria bater com a senha original: " + senha);
        assertFalse(encoder.matches(senha + "x", hash), "senha alterada não deveria bater");
    }

    @Test
    @DisplayName("BCrypt real: senha com exatamente 72 bytes é aceita; acima de 72 bytes o encode falha (limite do próprio BCrypt)")
    void should_confirm_real_bcrypt_72_byte_boundary() {
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);
        String senha72Bytes = "a1" + "x".repeat(70); // 72 bytes ASCII, aceita pela PasswordPolicy
        String hash = encoder.encode(senha72Bytes);
        assertTrue(encoder.matches(senha72Bytes, hash));

        String senha73Bytes = "a1" + "x".repeat(71); // já rejeitada por PasswordPolicy.isStrong antes de chegar aqui
        assertFalse(PasswordPolicy.isStrong(senha73Bytes));
    }
}
