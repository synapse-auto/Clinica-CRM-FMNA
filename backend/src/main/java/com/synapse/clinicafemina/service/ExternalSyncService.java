package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.IntegrationSyncLog;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.IntegrationSyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalSyncService {

    private static final ZoneId SYNC_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ExternalProviderFactory providerFactory;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final ExternalSyncTransactionService transactionService;
    private final IntegrationSyncLogService syncLogService;

    public ExternalSyncResult sincronizar(Clinica clinica) {
        return sincronizar(clinica, null, null);
    }

    public ExternalSyncResult sincronizar(Clinica clinica, LocalDate dataInicio, LocalDate dataFim) {
        validarPeriodoManual(dataInicio, dataFim);
        ExternalProviderType providerType = clinica.getExternalProvider();
        ExternalClinicProvider provider = providerFactory.getProvider(providerType);
        OffsetDateTime updatedAfter = resolverUpdatedAfter(clinica, providerType, dataInicio);
        Long runLogId = syncLogService.iniciar(
                clinica.getId(),
                providerType,
                dataInicio == null ? updatedAfter : inicioDoDia(dataInicio),
                dataInicio,
                dataFim
        );

        ExternalSyncProgress progress = new ExternalSyncProgress();
        String status = "SUCESSO";
        String mensagemErro = null;
        try {
            log.info(
                    "Sincronizacao externa iniciada: clinica={}, provider={}, dataInicio={}, dataFim={}, updatedAfter={}",
                    clinica.getId(), providerType, dataInicio, dataFim, updatedAfter);
            transactionService.sincronizar(
                    clinica, provider, updatedAfter, dataInicio, dataFim, progress);
        } catch (Exception e) {
            status = "FALHA_TOTAL";
            String erroResumo = safeErrorSummary(e);
            mensagemErro = "Falha na sincronizacao externa " + erroResumo;
            log.error(
                    "Falha na sincronizacao externa: clinica={}, provider={}, erroResumo={}",
                    clinica.getId(), providerType, erroResumo);
        }

        syncLogService.finalizar(runLogId, status, progress, mensagemErro);
        log.info(
                "Sincronizacao externa finalizada: clinica={}, provider={}, status={}, dataInicio={}, dataFim={}, pacientesProcessados={}, pacientesCriados={}, pacientesAtualizados={}, agendamentosProcessados={}, agendamentosCriados={}, agendamentosAtualizados={}, agendamentosIgnorados={}",
                clinica.getId(), providerType, status, dataInicio, dataFim,
                progress.getPacientesProcessados(), progress.getPacientesCriados(),
                progress.getPacientesAtualizados(), progress.getAgendamentosProcessados(),
                progress.getAgendamentosCriados(), progress.getAgendamentosAtualizados(),
                progress.getAgendamentosIgnorados());
        return progress.toResult(status);
    }

    private OffsetDateTime resolverUpdatedAfter(
            Clinica clinica,
            ExternalProviderType providerType,
            LocalDate dataInicio
    ) {
        if (dataInicio != null) {
            return null;
        }
        return syncLogRepository.findUltimoSucesso(clinica.getId(), providerType)
                .map(IntegrationSyncLog::getIniciadoEm)
                .orElse(null);
    }

    private void validarPeriodoManual(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null && dataFim == null) {
            return;
        }
        if (dataInicio == null || dataFim == null || dataFim.isBefore(dataInicio)) {
            throw new BadRequestException("Periodo de sincronizacao invalido");
        }
    }

    private OffsetDateTime inicioDoDia(LocalDate dataInicio) {
        return dataInicio.atTime(LocalTime.MIDNIGHT).atZone(SYNC_ZONE).toOffsetDateTime();
    }

    private String safeErrorSummary(Exception error) {
        if (error instanceof ExternalSyncTransactionService.SyncStageException stageException) {
            return "na etapa " + stageException.getStage() + ": " + stageException.getCauseType();
        }
        return "na etapa TRANSACAO_PRINCIPAL: " + error.getClass().getSimpleName();
    }
}
