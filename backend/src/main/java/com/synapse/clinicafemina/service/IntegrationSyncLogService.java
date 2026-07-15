package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.IntegrationSyncLog;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.IntegrationSyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class IntegrationSyncLogService {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final ClinicaRepository clinicaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long iniciar(
            Long clinicaId,
            ExternalProviderType providerType,
            OffsetDateTime updatedAfter,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        IntegrationSyncLog runLog = new IntegrationSyncLog();
        runLog.setClinica(clinicaRepository.getReferenceById(clinicaId));
        runLog.setExternalProvider(providerType);
        runLog.setUpdatedAfterUtilizado(updatedAfter);
        runLog.setDataInicio(dataInicio);
        runLog.setDataFim(dataFim);
        return syncLogRepository.saveAndFlush(runLog).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizar(
            Long runLogId,
            String status,
            ExternalSyncProgress progress,
            String mensagemErro
    ) {
        IntegrationSyncLog runLog = syncLogRepository.findById(runLogId)
                .orElseThrow(() -> new IllegalStateException("Log de sincronizacao nao encontrado"));
        runLog.setStatus(status);
        runLog.setMensagemErro(mensagemErro);
        runLog.setConcluidoEm(OffsetDateTime.now());
        runLog.setPacientesProcessados(progress.getPacientesProcessados());
        runLog.setPacientesCriados(progress.getPacientesCriados());
        runLog.setPacientesAtualizados(progress.getPacientesAtualizados());
        runLog.setAgendamentosProcessados(progress.getAgendamentosProcessados());
        runLog.setAgendamentosCriados(progress.getAgendamentosCriados());
        runLog.setAgendamentosAtualizados(progress.getAgendamentosAtualizados());
        runLog.setAgendamentosIgnorados(progress.getAgendamentosIgnorados());
        syncLogRepository.saveAndFlush(runLog);
    }
}
