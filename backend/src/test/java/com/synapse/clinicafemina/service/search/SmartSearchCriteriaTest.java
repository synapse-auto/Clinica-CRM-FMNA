package com.synapse.clinicafemina.service.search;

import com.synapse.clinicafemina.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartSearchCriteriaTest {

    @Test
    void should_normalize_accents_spaces_and_token_order_without_wildcards() {
        SmartSearchCriteria criteria = SmartSearchCriteria.from("  Silva  Joao%_ ");

        assertEquals(SmartSearchCriteria.MODE_TEXT, criteria.mode());
        assertEquals("SILVA JOAO", criteria.normalized());
        assertEquals("SILVA", criteria.token(0));
        assertEquals("JOAO", criteria.token(1));
        assertEquals("", criteria.token(2));
        assertNull(criteria.exactId());
    }

    @Test
    void should_normalize_phone_with_or_without_country_code() {
        SmartSearchCriteria withCountry = SmartSearchCriteria.from("+55 (83) 99999-0000");
        SmartSearchCriteria local = SmartSearchCriteria.from("(83) 99999-0000");

        assertEquals(SmartSearchCriteria.MODE_NUMERIC, withCountry.mode());
        assertEquals("5583999990000", withCountry.digits());
        assertEquals("83999990000", withCountry.localPhoneDigits());
        assertEquals("5583999990000", local.phoneWithCountryCode());
    }

    @Test
    void should_allow_exact_numeric_id_and_ignore_short_text_search() {
        assertEquals(42L, SmartSearchCriteria.from("42").exactId());
        assertEquals(SmartSearchCriteria.MODE_NONE, SmartSearchCriteria.from("a").mode());
    }

    @Test
    void should_limit_query_length_and_tokens() {
        assertThrows(BadRequestException.class, () -> SmartSearchCriteria.from("a".repeat(101)));
        SmartSearchCriteria criteria = SmartSearchCriteria.from("um dois tres quatro cinco seis");
        assertEquals(5, criteria.tokens().size());
    }
}
