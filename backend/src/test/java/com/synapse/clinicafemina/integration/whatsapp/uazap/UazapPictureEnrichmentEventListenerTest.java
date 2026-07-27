package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UazapPictureEnrichmentEventListener — ponte assíncrona pós-commit")
class UazapPictureEnrichmentEventListenerTest {

    @Mock
    private UazapProfilePhotoEnrichmentService enrichmentService;

    private WhatsappProperties properties(String provider, boolean enabled) {
        WhatsappProperties properties = new WhatsappProperties();
        properties.setProvider(provider);
        properties.getUazap().setPictureEnrichmentEnabled(enabled);
        return properties;
    }

    @Test
    @DisplayName("UAZAP com flag habilitada delega exatamente uma vez ao serviço")
    void enabledUazap_delegatesToEnrichmentService() {
        UazapPictureEnrichmentEventListener listener = new UazapPictureEnrichmentEventListener(
                enrichmentService, properties("UAZAP", true));

        listener.aoConfirmarMensagem(new UazapPictureEnrichmentRequestedEvent(42L, 7L));

        verify(enrichmentService).enriquecer(42L, 7L);
    }

    @Test
    @DisplayName("flag desabilitada ignora evento sem tocar no serviço ou banco")
    void disabledEnrichment_ignoresEvent() {
        UazapPictureEnrichmentEventListener listener = new UazapPictureEnrichmentEventListener(
                enrichmentService, properties("UAZAP", false));

        listener.aoConfirmarMensagem(new UazapPictureEnrichmentRequestedEvent(42L, 7L));

        verify(enrichmentService, never()).enriquecer(42L, 7L);
    }

    @Test
    @DisplayName("flag desabilitada impede claim, cliente UAZAP, parser e downloader")
    void disabledEnrichment_hasZeroAutomaticSideEffects() {
        WhatsappProperties properties = properties("UAZAP", false);
        UazapProfilePhotoClient photoClient = mock(UazapProfilePhotoClient.class);
        UazapPicturePayloadParser payloadParser = mock(UazapPicturePayloadParser.class);
        UazapProfilePhotoDownloader photoDownloader = mock(UazapProfilePhotoDownloader.class);
        PacienteFotoPerfilService fotoPerfilService = mock(PacienteFotoPerfilService.class);
        UazapProfilePhotoEnrichmentService realService = new UazapProfilePhotoEnrichmentService(
                properties, photoClient, payloadParser, photoDownloader, fotoPerfilService);
        UazapPictureEnrichmentEventListener listener =
                new UazapPictureEnrichmentEventListener(realService, properties);

        listener.aoConfirmarMensagem(new UazapPictureEnrichmentRequestedEvent(42L, 7L));

        verifyNoInteractions(fotoPerfilService, photoClient, payloadParser, photoDownloader);
    }

    @Test
    @DisplayName("provider META ignora evento mesmo com flag habilitada")
    void metaProvider_ignoresEvent() {
        UazapPictureEnrichmentEventListener listener = new UazapPictureEnrichmentEventListener(
                enrichmentService, properties("META", true));

        listener.aoConfirmarMensagem(new UazapPictureEnrichmentRequestedEvent(42L, 7L));

        verify(enrichmentService, never()).enriquecer(42L, 7L);
    }

    @Test
    @DisplayName("exceção do serviço de enriquecimento nunca propaga (webhook já foi respondido)")
    void serviceFailure_neverPropagates() {
        UazapPictureEnrichmentEventListener listener = new UazapPictureEnrichmentEventListener(
                enrichmentService, properties("UAZAP", true));
        when(enrichmentService.enriquecer(42L, 7L)).thenThrow(new RuntimeException("falha simulada"));

        assertThatCode(() -> listener.aoConfirmarMensagem(new UazapPictureEnrichmentRequestedEvent(42L, 7L)))
                .doesNotThrowAnyException();
    }
}
