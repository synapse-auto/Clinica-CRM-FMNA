package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.exception.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsappPhoneNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "'(83) 99999-9999',5583999999999",
            "'83 99999-9999',5583999999999",
            "'+55 83 99999-9999',5583999999999",
            "5583999999999,5583999999999",
            "'+351 912 345 678',351912345678"
    })
    void should_normalize_supported_phone_formats(String raw, String expected) {
        assertEquals(expected, WhatsappPhoneNormalizer.normalize(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234", "99999999999999999999", "0083999999999"})
    void should_reject_invalid_phone(String raw) {
        assertThrows(BadRequestException.class, () -> WhatsappPhoneNormalizer.normalize(raw));
    }

    @ParameterizedTest
    @CsvSource({
            "5583991114004,5583991114004,558391114004",
            "558391114004,558391114004,5583991114004"
    })
    void should_generate_bidirectional_safe_brazilian_mobile_aliases(
            String raw,
            String first,
            String second
    ) {
        assertEquals(Set.of(first, second), WhatsappPhoneNormalizer.safeAliases(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+351912345678", "558332221111"})
    void should_not_generate_alias_for_international_or_fixed_phone(String raw) {
        String normalized = WhatsappPhoneNormalizer.normalize(raw);
        assertEquals(Set.of(normalized), WhatsappPhoneNormalizer.safeAliases(raw));
    }
}
