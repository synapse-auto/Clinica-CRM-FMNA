package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.followup.ConfigStatusRequest;
import com.synapse.clinicafemina.dto.lembrete.ConsultaLembreteConfigRequest;
import com.synapse.clinicafemina.dto.lembrete.ConsultaLembreteConfigResponse;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ConsultaLembreteConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consulta-lembrete/config")
@RequiredArgsConstructor
public class ConsultaLembreteConfigController {

    private final ClinicaConfigService clinicaConfigService;
    private final ConsultaLembreteConfigService service;

    @GetMapping
    @PreAuthorize("hasRole('GESTOR')")
    public List<ConsultaLembreteConfigResponse> listar() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.listar(clinica);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GESTOR')")
    public ConsultaLembreteConfigResponse criar(@RequestBody @Valid ConsultaLembreteConfigRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.criar(clinica, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    public ConsultaLembreteConfigResponse atualizar(@PathVariable Long id,
                                                    @RequestBody @Valid ConsultaLembreteConfigRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.atualizar(clinica, id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('GESTOR')")
    public ConsultaLembreteConfigResponse alterarStatus(@PathVariable Long id,
                                                        @RequestBody @Valid ConfigStatusRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.alterarStatus(clinica, id, request);
    }
}
