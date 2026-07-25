package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.darwin.DarwinBackfillStatus;
import com.synapse.clinicafemina.integration.external.DarwinAgendaProvider;
import com.synapse.clinicafemina.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarwinBackfillService — job administrativo sequencial, idempotente e interrompivel")
class DarwinBackfillServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private DarwinAgendaProvider darwinAgendaProvider;

    private DarwinBackfillService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new DarwinBackfillService(pacienteRepository, darwinAgendaProvider);
        clinica = new Clinica();
        clinica.setId(1L);
    }

    @Test
    @DisplayName("status() inicial e IDLE antes de qualquer execucao")
    void status_isIdleInitially() {
        assertThat(service.status().status()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("iniciar() processa cada paciente com CPF conhecido via listarPorPaciente e conclui como COMPLETED")
    void iniciar_processesAllKnownPatientsAndCompletes() throws InterruptedException {
        Paciente p1 = new Paciente();
        p1.setId(1L);
        p1.setCpf("11144477735");
        Paciente p2 = new Paciente();
        p2.setId(2L);
        p2.setCpf("52998224725");
        when(pacienteRepository.findByClinicaIdAndCpfHashIsNotNull(1L)).thenReturn(List.of(p1, p2));

        DarwinBackfillStatus iniciado = service.iniciar(clinica);
        assertThat(iniciado.status()).isEqualTo("RUNNING");
        assertThat(iniciado.totalPacientes()).isEqualTo(2);

        DarwinBackfillStatus finalStatus = aguardarConclusao();

        assertThat(finalStatus.status()).isEqualTo("COMPLETED");
        assertThat(finalStatus.processados()).isEqualTo(2);
        assertThat(finalStatus.comErro()).isZero();
        verify(darwinAgendaProvider, times(2)).listarPorPaciente(any(), any());
    }

    @Test
    @DisplayName("iniciar() chamado enquanto ja esta RUNNING nao dispara uma segunda execucao (idempotente)")
    void iniciar_doesNotStartSecondJobWhileRunning() throws InterruptedException {
        Paciente p1 = new Paciente();
        p1.setId(1L);
        p1.setCpf("11144477735");
        when(pacienteRepository.findByClinicaIdAndCpfHashIsNotNull(1L)).thenReturn(List.of(p1));

        DarwinBackfillStatus primeira = service.iniciar(clinica);
        DarwinBackfillStatus segunda = service.iniciar(clinica);

        assertThat(segunda).isEqualTo(primeira);
        verify(pacienteRepository, times(1)).findByClinicaIdAndCpfHashIsNotNull(1L);
        aguardarConclusao();
    }

    private DarwinBackfillStatus aguardarConclusao() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            DarwinBackfillStatus atual = service.status();
            if (!"RUNNING".equals(atual.status())) {
                return atual;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Backfill nao concluiu dentro do tempo esperado no teste");
    }
}
