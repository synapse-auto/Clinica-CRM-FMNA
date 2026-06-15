package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ExternalSyncResult;
import com.synapse.clinicafemina.service.ExternalSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integracoes")
@RequiredArgsConstructor
public class IntegracaoController {

    private final ClinicaConfigService clinicaConfigService;
    private final ExternalSyncService externalSyncService;

    @PostMapping("/sincronizar")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<ExternalSyncResult> sincronizar() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return ResponseEntity.ok(externalSyncService.sincronizar(clinica));
    }
}
