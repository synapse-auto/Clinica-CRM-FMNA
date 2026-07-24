package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.uazap.UazapPictureDiagnosticoRequest;
import com.synapse.clinicafemina.dto.uazap.UazapPictureDiagnosticoResponse;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.service.UazapPictureDiagnosticoService;
import com.synapse.clinicafemina.service.UsuarioPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint TEMPORÁRIO de diagnóstico administrativo da foto de perfil UAZAP.
 *
 * <p>Desabilitado por padrão ({@code UAZAP_PICTURE_DIAGNOSTICS_ENABLED=false} → 404). Restrito a
 * GESTOR + {@code adminInterno=true} da própria clínica do paciente. Nunca aceita token, telefone
 * ou URL do frontend — resolve tudo a partir do {@code pacienteId} no backend. A resposta nunca
 * contém a URL completa da foto, apenas metadados sanitizados.</p>
 */
@RestController
@RequestMapping("/api/admin/integracoes/uazap/foto")
@RequiredArgsConstructor
@PreAuthorize("@usuarioPermissionService.podeDiagnosticarFotoUazap(authentication)")
public class UazapPictureDiagnosticoController {

    private final UazapPictureDiagnosticoService diagnosticoService;
    private final UsuarioPermissionService usuarioPermissionService;
    private final WhatsappProperties whatsappProperties;

    @PostMapping("/diagnostico")
    public ResponseEntity<UazapPictureDiagnosticoResponse> diagnosticar(
            @RequestBody @Valid UazapPictureDiagnosticoRequest request,
            Authentication authentication
    ) {
        if (!whatsappProperties.getUazap().isPictureDiagnosticsEnabled()) {
            return ResponseEntity.notFound().build();
        }
        Usuario admin = usuarioPermissionService.exigirAdminInterno(authentication);
        return ResponseEntity.ok(diagnosticoService.diagnosticar(admin.getClinica(), request.pacienteId()));
    }
}
