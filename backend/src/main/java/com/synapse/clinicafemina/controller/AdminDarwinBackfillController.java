package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.darwin.DarwinBackfillStatus;
import com.synapse.clinicafemina.exception.DarwinNotAvailableException;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.DarwinBackfillService;
import com.synapse.clinicafemina.service.DarwinConsultaService;
import com.synapse.clinicafemina.service.UsuarioPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backfill administrativo manual do espelho local de agendamentos Darwin.
 * Não roda automaticamente — apenas quando explicitamente disparado por um GESTOR
 * com adminInterno=true. Nunca aceita DARWIN_API_TOKEN pelo request; nunca retorna
 * CPF, nome de paciente ou qualquer dado clínico — apenas contadores de progresso.
 */
@RestController
@RequestMapping("/api/admin/integracoes/darwin/backfill-agendamentos")
@RequiredArgsConstructor
@PreAuthorize("@usuarioPermissionService.podeExecutarBackfillDarwin(authentication)")
public class AdminDarwinBackfillController {

    private final ClinicaConfigService clinicaConfigService;
    private final DarwinBackfillService backfillService;
    private final DarwinConsultaService darwinConsultaService;
    private final UsuarioPermissionService usuarioPermissionService;

    @PostMapping
    public DarwinBackfillStatus iniciar(Authentication authentication) {
        usuarioPermissionService.exigirAdminInterno(authentication);
        Clinica clinica = assertClinicaDarwinDisponivel();
        return backfillService.iniciar(clinica);
    }

    @GetMapping("/status")
    public DarwinBackfillStatus status(Authentication authentication) {
        usuarioPermissionService.exigirAdminInterno(authentication);
        return backfillService.status();
    }

    @DeleteMapping
    public DarwinBackfillStatus cancelar(Authentication authentication) {
        usuarioPermissionService.exigirAdminInterno(authentication);
        return backfillService.solicitarCancelamento();
    }

    private Clinica assertClinicaDarwinDisponivel() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        if (clinica.getExternalProvider() != ExternalProviderType.DARWIN) {
            throw new DarwinNotAvailableException("A clínica atual não utiliza a integração Darwin.");
        }
        if (!darwinConsultaService.isEnabled()) {
            throw new DarwinNotAvailableException("A integração Darwin não está habilitada.");
        }
        return clinica;
    }
}
