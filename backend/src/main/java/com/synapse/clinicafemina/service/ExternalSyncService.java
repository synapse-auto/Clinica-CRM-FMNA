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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalSyncService {

    private static final ZoneId SYNC_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ExternalProviderFactory providerFactory;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final ExternalSyncTransactionService transactionService;
    private final IntegrationSyncLogService syncLogService;
    private final ExternalSyncLockService lockService;

    public ExternalSyncResult sincronizar(Clinica clinica) {
        return sincronizar(clinica, null, null);
    }

    public ExternalSyncResult sincronizar(Clinica clinica, LocalDate dataInicio, LocalDate dataFim) {
        return sincronizar(clinica, dataInicio, dataFim, ExternalSyncOrigin.MANUAL);
    }

    public ExternalSyncResult sincronizar(
            Clinica clinica,
            LocalDate dataInicio,
            LocalDate dataFim,
            ExternalSyncOrigin origem
    ) {
        validarPeriodoManual(dataInicio, dataFim);
        if (clinica == null || clinica.getId() == null || clinica.getExternalProvider() == null) {
            throw new BadRequestException("Clinica ou provider externo nao configurado");
        }
        ExternalProviderType providerType = clinica.getExternalProvider();
        OffsetDateTime updatedAfter = resolverUpdatedAfter(clinica, providerType, dataInicio, origem);
        OffsetDateTime updatedAfterRegistrado = origem == ExternalSyncOrigin.AGENDADA || dataInicio == null
                ? updatedAfter
                : inicioDoDia(dataInicio);
        Optional<ExternalSyncLockService.LockHandle> lock = lockService.tryAcquire(clinica.getId(), providerType);
        if (lock.isEmpty()) {
            Long runLogId = iniciarLog(
                    clinica, providerType, updatedAfterRegistrado, dataInicio, dataFim, origem);
            ExternalSyncProgress ignoredProgress = new ExternalSyncProgress();
            String ignoredMessage = "Execucao ignorada porque ja existe uma sincronizacao ativa";
            syncLogService.finalizar(runLogId, "IGNORADA_CONCORRENCIA", ignoredProgress, ignoredMessage);
            log.info(
                    "Sincronizacao externa ignorada por concorrencia: clinica={}, provider={}, origem={}",
                    clinica.getId(), providerType, origem);
            return ignoredProgress.toResult("IGNORADA_CONCORRENCIA");
        }

        try (ExternalSyncLockService.LockHandle ignored = lock.orElseThrow()) {
            Long runLogId = iniciarLog(
                    clinica, providerType, updatedAfterRegistrado, dataInicio, dataFim, origem);
            ExternalSyncProgress progress = new ExternalSyncProgress();
            String status = "SUCESSO";
            String mensagemErro = null;
            try {
                ExternalClinicProvider provider = providerFactory.getProvider(providerType);
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
    }

    private Long iniciarLog(
            Clinica clinica,
            ExternalProviderType providerType,
            OffsetDateTime updatedAfter,
            LocalDate dataInicio,
            LocalDate dataFim,
            ExternalSyncOrigin origem
    ) {
        if (origem == ExternalSyncOrigin.MANUAL) {
            return syncLogService.iniciar(
                    clinica.getId(), providerType, updatedAfter, dataInicio, dataFim);
        }
        return syncLogService.iniciar(
                clinica.getId(), providerType, updatedAfter, dataInicio, dataFim, origem);
    }

    private OffsetDateTime resolverUpdatedAfter(
            Clinica clinica,
            ExternalProviderType providerType,
            LocalDate dataInicio,
            ExternalSyncOrigin origem
    ) {
        if (origem == ExternalSyncOrigin.MANUAL && dataInicio != null) {
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
            return "na etapa " + stageException.getStage() + ": "
                    + stageException.getCauseType() + stageException.getTechnicalDetails();
        }
        return "na etapa TRANSACAO_PRINCIPAL: " + error.getClass().getSimpleName();
    }
}
