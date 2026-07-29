package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsappContactNameServiceTest {

    private final WhatsappContactNameService service = new WhatsappContactNameService();

    @Test
    void should_strip_html_trim_and_preserve_unicode_letters() {
        assertEquals(
                "Maria Ávila",
                service.sanitizeRequired(
                        "  <strong>Maria</strong>   Ávila  ",
                        "5583999999999"
                )
        );
    }

    @Test
    void should_reject_empty_literal_null_symbol_only_and_phone_as_name() {
        assertThrows(
                BadRequestException.class,
                () -> service.sanitizeRequired("null", "5583999999999")
        );
        assertThrows(
                BadRequestException.class,
                () -> service.sanitizeRequired("***", "5583999999999")
        );
        assertThrows(
                BadRequestException.class,
                () -> service.sanitizeRequired("+5583999999999", "5583999999999")
        );
    }
}
