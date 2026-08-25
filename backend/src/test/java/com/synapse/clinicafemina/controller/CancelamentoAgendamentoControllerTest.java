package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.service.CancelamentoAgendamentoService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelamentoAgendamentoControllerTest {
    @Mock private ClinicaConfigService clinicaConfigService;
    @Mock private CancelamentoAgendamentoService service;

    @Test
    void should_delete_history_for_the_current_clinic() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        CancelamentoAgendamentoController controller = new CancelamentoAgendamentoController(clinicaConfigService, service);

        controller.apagarTodos();

        verify(service).apagarTodos(clinica);
    }

    @Test
    void should_require_manager_role_for_delete_endpoint() throws NoSuchMethodException {
        Method method = CancelamentoAgendamentoController.class.getMethod("apagarTodos");
        assertEquals("hasRole('GESTOR')", method.getAnnotation(PreAuthorize.class).value());
    }
}
