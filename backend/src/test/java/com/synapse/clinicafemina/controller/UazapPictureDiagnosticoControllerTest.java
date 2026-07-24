package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.uazap.UazapPictureDiagnosticoRequest;
import com.synapse.clinicafemina.dto.uazap.UazapPictureDiagnosticoResponse;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.service.UazapPictureDiagnosticoService;
import com.synapse.clinicafemina.service.UsuarioPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UazapPictureDiagnosticoController — branch de flag e delegação (sem contexto Spring)")
class UazapPictureDiagnosticoControllerTest {

    @Mock
    private UazapPictureDiagnosticoService diagnosticoService;

    @Mock
    private UsuarioPermissionService usuarioPermissionService;

    private WhatsappProperties whatsappProperties;
    private UazapPictureDiagnosticoController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        whatsappProperties = new WhatsappProperties();
        controller = new UazapPictureDiagnosticoController(diagnosticoService, usuarioPermissionService, whatsappProperties);
        authentication = new UsernamePasswordAuthenticationToken("admin", "senha");
    }

    @Test
    @DisplayName("flag desabilitada (padrão): retorna 404 sem consultar permissão ou serviço")
    void flagDisabled_returnsNotFoundWithoutTouchingPermissionOrService() {
        ResponseEntity<UazapPictureDiagnosticoResponse> response =
                controller.diagnosticar(new UazapPictureDiagnosticoRequest(1L), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(usuarioPermissionService, never()).exigirAdminInterno(any());
        verify(diagnosticoService, never()).diagnosticar(any(), any());
    }

    @Test
    @DisplayName("flag habilitada: exige admin interno e delega ao serviço com a clínica do admin")
    void flagEnabled_delegatesToServiceWithAdminClinic() {
        whatsappProperties.getUazap().setPictureDiagnosticsEnabled(true);
        Clinica clinica = new Clinica();
        clinica.setId(9L);
        Usuario admin = new Gestor();
        admin.setClinica(clinica);
        when(usuarioPermissionService.exigirAdminInterno(authentication)).thenReturn(admin);
        UazapPictureDiagnosticoResponse esperado = new UazapPictureDiagnosticoResponse(
                200, "application/json", 10, "JSON", java.util.List.of(), true, false, false, true, null);
        when(diagnosticoService.diagnosticar(clinica, 1L)).thenReturn(esperado);

        ResponseEntity<UazapPictureDiagnosticoResponse> response =
                controller.diagnosticar(new UazapPictureDiagnosticoRequest(1L), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(esperado);
        verify(diagnosticoService).diagnosticar(clinica, 1L);
    }
}
