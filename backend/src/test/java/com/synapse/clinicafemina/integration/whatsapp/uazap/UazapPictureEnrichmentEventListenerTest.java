package com.synapse.clinicafemina.integration.whatsapp.uazap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UazapPictureEnrichmentEventListener — ponte assíncrona pós-commit")
class UazapPictureEnrichmentEventListenerTest {

    @Mock
    private UazapProfilePhotoEnrichmentService enrichmentService;

    @Test
    @DisplayName("delega ao serviço de enriquecimento com o pacienteId do evento")
    void delegatesToEnrichmentService() {
        UazapPictureEnrichmentEventListener listener = new UazapPictureEnrichmentEventListener(enrichmentService);

        listener.aoConfirmarMensagem(new UazapPictureEnrichmentRequestedEvent(42L));

        verify(enrichmentService).enriquecer(42L);
    }

    @Test
    @DisplayName("exceção do serviço de enriquecimento nunca propaga (webhook já foi respondido)")
    void serviceFailure_neverPropagates() {
        UazapPictureEnrichmentEventListener listener = new UazapPictureEnrichmentEventListener(enrichmentService);
        when(enrichmentService.enriquecer(42L)).thenThrow(new RuntimeException("falha simulada"));

        assertThatCode(() -> listener.aoConfirmarMensagem(new UazapPictureEnrichmentRequestedEvent(42L)))
                .doesNotThrowAnyException();
    }
}
