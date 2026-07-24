package com.synapse.clinicafemina.integration.whatsapp.uazap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ponte assíncrona entre o evento publicado pelo webhook e {@link UazapProfilePhotoEnrichmentService}.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} garante que só executa depois que
 * mensagem/paciente/atendimento já foram commitados. {@code @Async} garante que roda em outra
 * thread, fora da requisição HTTP do webhook. Qualquer exceção é capturada aqui — nunca deve
 * propagar para o publisher do evento nem para o executor assíncrono.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UazapPictureEnrichmentEventListener {

    private final UazapProfilePhotoEnrichmentService enrichmentService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmarMensagem(UazapPictureEnrichmentRequestedEvent event) {
        try {
            enrichmentService.enriquecer(event.pacienteId());
        } catch (Exception exception) {
            log.warn("Falha não tratada no enriquecimento assíncrono de foto UAZAP; fluxo do webhook não é afetado. tipoErro={}",
                    exception.getClass().getSimpleName());
        }
    }
}
