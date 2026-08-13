package com.synapse.clinicafemina.domain;

import com.synapse.clinicafemina.security.crypto.AesGcmConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MensagemSchemaTest {

    @Test
    void should_map_whatsapp_message_id_with_meta_wamid_length() throws NoSuchFieldException {
        Column column = Mensagem.class.getDeclaredField("whatsappMessageId").getAnnotation(Column.class);

        assertEquals(255, column.length());
    }

    @Test
    void should_encrypt_interactive_content_in_its_own_database_column() throws NoSuchFieldException {
        var field = Mensagem.class.getDeclaredField("conteudoInterativo");
        Column column = field.getAnnotation(Column.class);
        Convert convert = field.getAnnotation(Convert.class);

        assertEquals("conteudo_interativo", column.name());
        assertEquals(AesGcmConverter.class, convert.converter());
    }
}
