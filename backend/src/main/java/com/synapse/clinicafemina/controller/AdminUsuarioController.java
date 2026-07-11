package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioCreateRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.dto.auth.ResetPasswordRequest;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.AdminUsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("@usuarioPermissionService.podeGerenciarUsuarios(authentication)")
public class AdminUsuarioController {

    private final ClinicaConfigService clinicaConfigService;
    private final AdminUsuarioService adminUsuarioService;

    @GetMapping
    public List<EquipeUsuarioResponse> listar() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return adminUsuarioService.listar(clinica);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipeUsuarioResponse criarUsuario(
            @RequestBody @Valid EquipeUsuarioCreateRequest request
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return adminUsuarioService.criarUsuario(clinica, request);
    }

    @PatchMapping("/{id}/status")
    public EquipeUsuarioResponse alterarStatus(
            @PathVariable Long id,
            @RequestBody @Valid StatusRequest request
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return adminUsuarioService.alterarStatus(clinica, id, request);
    }

    @PatchMapping("/{id}/resetar-senha")
    public EquipeUsuarioResponse resetarSenha(
            @PathVariable Long id,
            @RequestBody @Valid ResetPasswordRequest request
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return adminUsuarioService.resetarSenha(clinica, id, request);
    }
}
