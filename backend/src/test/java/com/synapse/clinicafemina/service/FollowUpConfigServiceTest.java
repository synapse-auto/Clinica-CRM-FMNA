package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.FollowUpConfig;
import com.synapse.clinicafemina.dto.followup.FollowUpConfigRequest;
import com.synapse.clinicafemina.dto.followup.ConfigStatusRequest;
import com.synapse.clinicafemina.repository.FollowUpConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpConfigServiceTest {

    @Mock
    private FollowUpConfigRepository repository;

    private FollowUpConfigService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new FollowUpConfigService(repository);
        clinica = new Clinica();
        clinica.setId(9L);
    }

    @Test
    void should_create_follow_up_config_for_current_clinic() {
        FollowUpConfigRequest request = new FollowUpConfigRequest(
                "Pos-consulta",
                "Mensagem depois do exame",
                true,
                "POS_CONSULTA",
                "WHATSAPP",
                1,
                "DIAS",
                LocalTime.of(9, 0),
                "Ola, [Nome]",
                "{\"dias\":1}"
        );
        when(repository.save(any(FollowUpConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.criar(clinica, request);

        ArgumentCaptor<FollowUpConfig> captor = ArgumentCaptor.forClass(FollowUpConfig.class);
        verify(repository).save(captor.capture());
        FollowUpConfig saved = captor.getValue();
        assertSame(clinica, saved.getClinica());
        assertEquals("Pos-consulta", saved.getNome());
        assertEquals("POS_CONSULTA", saved.getGatilho());
        assertEquals("WHATSAPP", saved.getCanal());
        assertEquals(LocalTime.of(9, 0), saved.getHorarioEnvio());
    }

    @Test
    void should_update_follow_up_config_status_by_clinic() {
        FollowUpConfig config = new FollowUpConfig();
        config.setId(11L);
        config.setClinica(clinica);
        config.setNome("Pos-consulta");
        config.setAtivo(true);
        when(repository.findByIdAndClinicaId(11L, 9L)).thenReturn(Optional.of(config));
        when(repository.save(any(FollowUpConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.alterarStatus(clinica, 11L, new ConfigStatusRequest(false));

        assertFalse(config.getAtivo());
    }
}
