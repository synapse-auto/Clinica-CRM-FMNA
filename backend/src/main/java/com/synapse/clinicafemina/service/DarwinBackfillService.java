package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.darwin.DarwinBackfillStatus;
import com.synapse.clinicafemina.integration.external.DarwinAgendaProvider;
import com.synapse.clinicafemina.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backfill administrativo manual: percorre pacientes conhecidos da clínica (com CPF
 * válido) e consulta agendamentos Darwin por CPF, alimentando o espelho local.
 * Sequencial (concorrência 1) com pausa entre chamadas (rate limit), idempotente
 * (reaproveita {@link DarwinAgendaProvider#listarPorPaciente}, já idempotente),
 * interrompível, e nunca é disparado automaticamente — requer chamada explícita ao
 * endpoint administrativo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DarwinBackfillService {

    private static final long PAUSA_ENTRE_CHAMADAS_MS = 200;

    private final PacienteRepository pacienteRepository;
    private final DarwinAgendaProvider darwinAgendaProvider;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "darwin-backfill");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<DarwinBackfillStatus> status = new AtomicReference<>(DarwinBackfillStatus.idle());
    private volatile boolean cancelamentoSolicitado = false;

    public DarwinBackfillStatus iniciar(Clinica clinica) {
        DarwinBackfillStatus atual = status.get();
        if ("RUNNING".equals(atual.status())) {
            return atual;
        }
        List<Paciente> pacientes = pacienteRepository.findByClinicaIdAndCpfHashIsNotNull(clinica.getId());
        cancelamentoSolicitado = false;
        DarwinBackfillStatus iniciado = new DarwinBackfillStatus(
                "RUNNING", pacientes.size(), 0, 0, OffsetDateTime.now(), null);
        status.set(iniciado);
        executor.submit(() -> executar(clinica, pacientes));
        return iniciado;
    }

    public DarwinBackfillStatus status() {
        return status.get();
    }

    public DarwinBackfillStatus solicitarCancelamento() {
        cancelamentoSolicitado = true;
        return status.get();
    }

    private void executar(Clinica clinica, List<Paciente> pacientes) {
        int processados = 0;
        int comErro = 0;
        for (Paciente paciente : pacientes) {
            if (cancelamentoSolicitado) {
                status.updateAndGet(s -> s.comStatus("CANCELLED").finalizadoAgora());
                return;
            }
            try {
                darwinAgendaProvider.listarPorPaciente(clinica, paciente.getCpf());
            } catch (Exception e) {
                comErro++;
                log.warn("Falha sanitizada no backfill Darwin: tipoErro={}", e.getClass().getSimpleName());
            }
            processados++;
            int processadosFinal = processados;
            int comErroFinal = comErro;
            status.updateAndGet(s -> s.comProgresso(processadosFinal, comErroFinal));
            try {
                Thread.sleep(PAUSA_ENTRE_CHAMADAS_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                status.updateAndGet(s -> s.comStatus("CANCELLED").finalizadoAgora());
                return;
            }
        }
        status.updateAndGet(s -> s.comStatus("COMPLETED").finalizadoAgora());
    }
}
