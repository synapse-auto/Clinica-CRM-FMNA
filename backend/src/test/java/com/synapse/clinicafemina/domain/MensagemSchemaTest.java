package com.synapse.clinicafemina.domain;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MensagemSchemaTest {

    @Test
    void should_map_whatsapp_message_id_with_meta_wamid_length() throws NoSuchFieldException {
        Column column = Mensagem.class.getDeclaredField("whatsappMessageId").getAnnotation(Column.class);

        assertEquals(255, column.length());
    }
}
