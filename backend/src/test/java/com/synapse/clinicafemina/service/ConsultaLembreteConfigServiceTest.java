package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.ConsultaLembreteConfig;
import com.synapse.clinicafemina.dto.followup.ConfigStatusRequest;
import com.synapse.clinicafemina.dto.lembrete.ConsultaLembreteConfigRequest;
import com.synapse.clinicafemina.repository.ConsultaLembreteConfigRepository;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaLembreteConfigServiceTest {

    @Mock
    private ConsultaLembreteConfigRepository repository;

    private ConsultaLembreteConfigService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new ConsultaLembreteConfigService(repository);
        clinica = new Clinica();
        clinica.setId(9L);
    }

    @Test
    void should_create_consulta_lembrete_config_for_current_clinic() {
        ConsultaLembreteConfigRequest request = new ConsultaLembreteConfigRequest(
                "Lembrete 1 dia antes",
                "Lembra consulta no dia anterior",
                true,
                "WHATSAPP",
                1,
                "DIAS",
                LocalTime.of(9, 0),
                true,
                true,
                true,
                "Ola, sua consulta esta chegando",
                "{\"janela\":\"manha\"}"
        );
        when(repository.save(any(ConsultaLembreteConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.criar(clinica, request);

        ArgumentCaptor<ConsultaLembreteConfig> captor = ArgumentCaptor.forClass(ConsultaLembreteConfig.class);
        verify(repository).save(captor.capture());
        ConsultaLembreteConfig saved = captor.getValue();
        assertSame(clinica, saved.getClinica());
        assertEquals("Lembrete 1 dia antes", saved.getNome());
        assertEquals(1, saved.getAntecedenciaQuantidade());
        assertEquals("DIAS", saved.getAntecedenciaUnidade());
        assertEquals(LocalTime.of(9, 0), saved.getHorarioEnvio());
    }

    @Test
    void should_update_consulta_lembrete_status_by_clinic() {
        ConsultaLembreteConfig config = new ConsultaLembreteConfig();
        config.setId(11L);
        config.setClinica(clinica);
        config.setNome("Lembrete 2 horas antes");
        config.setAtivo(true);
        when(repository.findByIdAndClinicaId(11L, 9L)).thenReturn(Optional.of(config));
        when(repository.save(any(ConsultaLembreteConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.alterarStatus(clinica, 11L, new ConfigStatusRequest(false));

        assertFalse(config.getAtivo());
    }
}
