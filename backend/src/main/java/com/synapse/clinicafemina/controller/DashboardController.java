package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.dashboard.DashboardPeriodo;
import com.synapse.clinicafemina.dto.dashboard.DashboardResponse;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ClinicaConfigService clinicaConfigService;
    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public DashboardResponse obterDashboard(
            @RequestParam(defaultValue = "DIA") DashboardPeriodo periodo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return dashboardService.obterDashboard(clinica, periodo, data);
    }
}
