package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.MensagemFestivaConfig;
import com.synapse.clinicafemina.dto.followup.ConfigStatusRequest;
import com.synapse.clinicafemina.dto.lembrete.MensagemFestivaConfigRequest;
import com.synapse.clinicafemina.repository.MensagemFestivaConfigRepository;
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
class MensagemFestivaConfigServiceTest {

    @Mock
    private MensagemFestivaConfigRepository repository;

    private MensagemFestivaConfigService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new MensagemFestivaConfigService(repository);
        clinica = new Clinica();
        clinica.setId(9L);
    }

    @Test
    void should_create_mensagem_festiva_config_for_current_clinic() {
        MensagemFestivaConfigRequest request = new MensagemFestivaConfigRequest(
                "NATAL",
                "Natal",
                "12-25",
                true,
                "WHATSAPP",
                "Feliz Natal!",
                "{\"publico\":\"ativos\"}"
        );
        when(repository.save(any(MensagemFestivaConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.criar(clinica, request);

        ArgumentCaptor<MensagemFestivaConfig> captor = ArgumentCaptor.forClass(MensagemFestivaConfig.class);
        verify(repository).save(captor.capture());
        MensagemFestivaConfig saved = captor.getValue();
        assertSame(clinica, saved.getClinica());
        assertEquals("NATAL", saved.getChave());
        assertEquals("Natal", saved.getNome());
        assertEquals("12-25", saved.getMesDia());
    }

    @Test
    void should_update_mensagem_festiva_status_by_clinic() {
        MensagemFestivaConfig config = new MensagemFestivaConfig();
        config.setId(12L);
        config.setClinica(clinica);
        config.setChave("ANO_NOVO");
        config.setAtivo(true);
        when(repository.findByIdAndClinicaId(12L, 9L)).thenReturn(Optional.of(config));
        when(repository.save(any(MensagemFestivaConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.alterarStatus(clinica, 12L, new ConfigStatusRequest(false));

        assertFalse(config.getAtivo());
    }
}
