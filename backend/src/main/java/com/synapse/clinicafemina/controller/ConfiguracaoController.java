package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.configuracao.ClinicaAtualResponse;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuracoes")
@RequiredArgsConstructor
public class ConfiguracaoController {

    private final ClinicaConfigService clinicaConfigService;

    @GetMapping("/clinica-atual")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public ClinicaAtualResponse clinicaAtual() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return new ClinicaAtualResponse(
                clinica.getNome(),
                clinica.getSlug(),
                clinica.getTipoClinica(),
                clinica.getCorPrimaria(),
                clinica.getLogoUrl(),
                Boolean.TRUE.equals(clinica.getUsaCirurgiasNaAgenda()),
                Boolean.TRUE.equals(clinica.getFollowUpAutomatico()),
                Boolean.TRUE.equals(clinica.getUsaN8n()),
                clinica.getN8nWebhookUrl() != null && !clinica.getN8nWebhookUrl().isBlank()
        );
    }
}
