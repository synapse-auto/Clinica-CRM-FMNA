package com.synapse.clinicafemina.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AtendimentoModoIaScheduler {

    private final AtendimentoService atendimentoService;

    @Scheduled(
            fixedDelayString = "${app.atendimentos.retorno-ia-fixed-delay-ms:1800000}",
            initialDelayString = "${app.atendimentos.retorno-ia-initial-delay-ms:60000}"
    )
    public void retornarAtendimentosHumanosExpirados() {
        int total = atendimentoService.retornarHumanosExpiradosParaIa(OffsetDateTime.now());
        if (total > 0) {
            log.info("Atendimentos retornados automaticamente para IA: total={}", total);
        }
    }
}
