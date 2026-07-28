package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.exception.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
}
