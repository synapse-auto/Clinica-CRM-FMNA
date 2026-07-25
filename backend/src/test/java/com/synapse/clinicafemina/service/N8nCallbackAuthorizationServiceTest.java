package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class N8nCallbackAuthorizationServiceTest {

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Mock
    private ClinicaConfigService clinicaConfigService;

    @Test
    void should_authorize_ultramedical_callback_when_n8n_is_enabled() {
        Clinica clinica = clinica(7L, "ultramedical", true);
        N8nCallbackAuthorizationService service = service("ultra-secret");
        permitirAtendimento(clinica, 30L);

        N8nCallbackAuthorizationService.Autorizacao autorizacao =
                service.autorizar("ultra-secret", 30L);

        assertEquals(7L, autorizacao.clinicaId());
    }

    @Test
    void should_authorize_fmna_callback_when_n8n_is_enabled() {
        Clinica clinica = clinica(9L, "fmna", true);
        N8nCallbackAuthorizationService service = service("fmna-secret");
        permitirAtendimento(clinica, 88L);

        N8nCallbackAuthorizationService.Autorizacao autorizacao =
                service.autorizar("fmna-secret", 88L);

        assertEquals(9L, autorizacao.clinicaId());
    }

    @Test
    void should_reject_secrets_from_another_deployment() {
        N8nCallbackAuthorizationService ultraService = service("ultra-secret");
        N8nCallbackAuthorizationService fmnaService = service("fmna-secret");

        assertThrows(
                BadCredentialsException.class,
                () -> ultraService.autorizar("fmna-secret", 30L)
        );
        assertThrows(
                BadCredentialsException.class,
                () -> fmnaService.autorizar("ultra-secret", 30L)
        );

        verify(clinicaConfigService, never()).obterClinicaAtual();
        verify(atendimentoRepository, never()).findByIdAndClinicaId(any(), any());
    }

    @Test
    void should_reject_callback_when_deployment_secret_is_not_configured() {
        N8nCallbackAuthorizationService service = service(" ");

        assertThrows(
                BadCredentialsException.class,
                () -> service.autorizar("qualquer-secret", 30L)
        );

        verify(clinicaConfigService, never()).obterClinicaAtual();
        verify(atendimentoRepository, never()).findByIdAndClinicaId(any(), any());
    }

    @Test
    void should_reject_callback_when_n8n_is_disabled_for_attendance_clinic() {
        Clinica clinicaConfigurada = clinica(7L, "deployment", true);
        Clinica clinicaDoAtendimento = clinica(7L, "qualquer-slug", false);
        Atendimento atendimento = atendimento(30L, clinicaDoAtendimento);
        N8nCallbackAuthorizationService service = service("callback-secret");
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaConfigurada);
        when(atendimentoRepository.findByIdAndClinicaId(30L, 7L)).thenReturn(Optional.of(atendimento));

        assertThrows(
                AccessDeniedException.class,
                () -> service.autorizar("callback-secret", 30L)
        );
    }

    @Test
    void should_not_expose_attendance_from_another_clinic() {
        Clinica clinicaConfigurada = clinica(7L, "deployment", true);
        N8nCallbackAuthorizationService service = service("callback-secret");
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaConfigurada);
        when(atendimentoRepository.findByIdAndClinicaId(30L, 7L)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.autorizar("callback-secret", 30L)
        );

        verify(atendimentoRepository).findByIdAndClinicaId(30L, 7L);
    }

    // ---- autorizarClinica(secret): variante Agenda, sem atendimentoId ----

    @Test
    void should_authorize_agenda_callback_and_resolve_deployment_clinic() {
        Clinica clinica = clinica(9L, "fmna", true);
        N8nCallbackAuthorizationService service = service("fmna-secret");
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);

        Clinica autorizada = service.autorizarClinica("fmna-secret");

        assertEquals(9L, autorizada.getId());
    }

    @Test
    void should_reject_agenda_callback_secrets_from_another_deployment() {
        N8nCallbackAuthorizationService ultraService = service("ultra-secret");
        N8nCallbackAuthorizationService fmnaService = service("fmna-secret");

        assertThrows(
                BadCredentialsException.class,
                () -> ultraService.autorizarClinica("fmna-secret")
        );
        assertThrows(
                BadCredentialsException.class,
                () -> fmnaService.autorizarClinica("ultra-secret")
        );

        verify(clinicaConfigService, never()).obterClinicaAtual();
    }

    @Test
    void should_reject_agenda_callback_when_deployment_secret_is_not_configured() {
        N8nCallbackAuthorizationService service = service(" ");

        assertThrows(
                BadCredentialsException.class,
                () -> service.autorizarClinica("qualquer-secret")
        );

        verify(clinicaConfigService, never()).obterClinicaAtual();
    }

    @Test
    void should_reject_agenda_callback_when_n8n_is_disabled_for_deployment_clinic() {
        Clinica clinicaConfigurada = clinica(7L, "deployment", false);
        N8nCallbackAuthorizationService service = service("callback-secret");
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaConfigurada);

        assertThrows(
                AccessDeniedException.class,
                () -> service.autorizarClinica("callback-secret")
        );
    }

    @Test
    void should_isolate_overlapping_clinica_ids_between_two_deployments() {
        // Duas clinicas com o MESMO id (colisao hipotetica de dados/seed), cada uma
        // resolvida por um ClinicaConfigService independente (um por deployment). O
        // secret de um deployment nunca deve autorizar o outro, mesmo com ids iguais,
        // e a tentativa cruzada nunca deve sequer consultar o resolver do deployment alvo.
        ClinicaConfigService clinicaConfigServiceUltra = org.mockito.Mockito.mock(ClinicaConfigService.class);
        ClinicaConfigService clinicaConfigServiceFmna = org.mockito.Mockito.mock(ClinicaConfigService.class);
        Clinica clinicaUltra = clinica(7L, "ultramedical", true);
        Clinica clinicaFmna = clinica(7L, "fmna", true);
        N8nCallbackAuthorizationService ultraService =
                new N8nCallbackAuthorizationService(atendimentoRepository, clinicaConfigServiceUltra, "ultra-secret");
        N8nCallbackAuthorizationService fmnaService =
                new N8nCallbackAuthorizationService(atendimentoRepository, clinicaConfigServiceFmna, "fmna-secret");

        assertThrows(BadCredentialsException.class, () -> ultraService.autorizarClinica("fmna-secret"));
        assertThrows(BadCredentialsException.class, () -> fmnaService.autorizarClinica("ultra-secret"));
        verify(clinicaConfigServiceUltra, never()).obterClinicaAtual();
        verify(clinicaConfigServiceFmna, never()).obterClinicaAtual();

        when(clinicaConfigServiceUltra.obterClinicaAtual()).thenReturn(clinicaUltra);
        when(clinicaConfigServiceFmna.obterClinicaAtual()).thenReturn(clinicaFmna);

        Clinica resolvidaUltra = ultraService.autorizarClinica("ultra-secret");
        Clinica resolvidaFmna = fmnaService.autorizarClinica("fmna-secret");

        assertEquals("ultramedical", resolvidaUltra.getSlug());
        assertEquals("fmna", resolvidaFmna.getSlug());
    }

    private N8nCallbackAuthorizationService service(String callbackSecret) {
        return new N8nCallbackAuthorizationService(
                atendimentoRepository,
                clinicaConfigService,
                callbackSecret
        );
    }

    private void permitirAtendimento(Clinica clinica, Long atendimentoId) {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinica.getId()))
                .thenReturn(Optional.of(atendimento(atendimentoId, clinica)));
    }

    private Atendimento atendimento(Long id, Clinica clinica) {
        Atendimento atendimento = new Atendimento();
        atendimento.setId(id);
        atendimento.setClinica(clinica);
        return atendimento;
    }

    private Clinica clinica(Long id, String slug, boolean usaN8n) {
        Clinica clinica = new Clinica();
        clinica.setId(id);
        clinica.setSlug(slug);
        clinica.setUsaN8n(usaN8n);
        return clinica;
    }
}
