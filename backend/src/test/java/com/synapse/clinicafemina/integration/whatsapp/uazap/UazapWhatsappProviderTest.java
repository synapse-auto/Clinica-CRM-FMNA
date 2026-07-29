package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappPhoneNormalizer;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappRecipientResolution;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UazapWhatsappProviderTest {

    private final UazapClient client = mock(UazapClient.class);
    private final UazapWhatsappProvider provider = new UazapWhatsappProvider(client);

    @Test
    void should_use_provider_confirmed_legacy_chat_id_and_send_only_once() {
        WhatsappRecipientResolution resolution = provider.resolveRecipient(
                "558391114004",
                "5583991114004",
                WhatsappPhoneNormalizer.safeAliases("5583991114004")
        );
        when(client.sendText("558391114004", "Primeira mensagem"))
                .thenReturn(new WhatsappSendResult("UZ-1", provider.getType()));

        provider.sendText(resolution.recipient(), "Primeira mensagem");

        assertEquals("558391114004", resolution.recipient());
        verify(client).sendText("558391114004", "Primeira mensagem");
        verify(client, never()).sendText("5583991114004", "Primeira mensagem");
    }

    @Test
    void should_send_new_mobile_contact_to_its_registered_phone_once() {
        WhatsappRecipientResolution resolution = provider.resolveRecipient(
                null,
                "5583991114004",
                WhatsappPhoneNormalizer.safeAliases("5583991114004")
        );
        when(client.sendText("5583991114004", "Primeira mensagem"))
                .thenReturn(new WhatsappSendResult("UZ-2", provider.getType()));

        provider.sendText(resolution.recipient(), "Primeira mensagem");

        assertEquals("5583991114004", resolution.recipient());
        assertEquals("TELEFONE_CADASTRAL", resolution.source());
        verify(client).sendText("5583991114004", "Primeira mensagem");
        verify(client, never()).sendText("558391114004", "Primeira mensagem");
    }

    @Test
    void should_send_existing_legacy_contact_to_its_registered_phone_once() {
        WhatsappRecipientResolution resolution = provider.resolveRecipient(
                null,
                "558391114004",
                WhatsappPhoneNormalizer.safeAliases("558391114004")
        );
        when(client.sendText("558391114004", "Primeira mensagem"))
                .thenReturn(new WhatsappSendResult("UZ-3", provider.getType()));

        provider.sendText(resolution.recipient(), "Primeira mensagem");

        assertEquals("558391114004", resolution.recipient());
        verify(client).sendText("558391114004", "Primeira mensagem");
        verify(client, never()).sendText("5583991114004", "Primeira mensagem");
    }

    @Test
    void should_allow_single_unambiguous_international_recipient() {
        WhatsappRecipientResolution resolution = provider.resolveRecipient(
                null,
                "351912345678",
                WhatsappPhoneNormalizer.safeAliases("+351912345678")
        );

        assertEquals("351912345678", resolution.recipient());
        assertEquals("TELEFONE_CADASTRAL", resolution.source());
    }
}
