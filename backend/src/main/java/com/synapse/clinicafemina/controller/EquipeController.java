package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.equipe.EquipeResponse;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioCreateRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.EquipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipe")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GESTOR')")
public class EquipeController {

    private final ClinicaConfigService clinicaConfigService;
    private final EquipeService equipeService;

    @GetMapping
    public EquipeResponse listar() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return equipeService.listar(clinica);
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipeUsuarioResponse criarUsuario(
            @RequestBody @Valid EquipeUsuarioCreateRequest request
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return equipeService.criarUsuario(clinica, request);
    }
}
