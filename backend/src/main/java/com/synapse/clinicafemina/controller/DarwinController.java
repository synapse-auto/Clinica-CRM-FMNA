package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.service.DarwinSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint para disparo manual da sincronização com a API Darwin.
 * Apenas usuários com a role GESTOR podem executar.
 */
@Slf4j
@RestController
@RequestMapping("/api/integracoes/darwin")
@RequiredArgsConstructor
public class DarwinController {

    private final DarwinSyncService darwinSyncService;

    @PostMapping("/sincronizar")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<String> forceSync() {
        log.info("Sincronização manual com Darwin disparada pelo gestor.");
        // Roda de forma síncrona para que a requisição espere,
        // ou poderia ser assíncrona dependendo do tempo. Mantido síncrono para simplicidade da API.
        darwinSyncService.sync();
        return ResponseEntity.ok("Sincronização com o Darwin concluída.");
    }
}
