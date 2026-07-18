package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioCreateRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.dto.auth.ResetPasswordRequest;
import com.synapse.clinicafemina.service.AdminUsuarioService;
import com.synapse.clinicafemina.service.UsuarioPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("@usuarioPermissionService.podeGerenciarUsuarios(authentication)")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;
    private final UsuarioPermissionService usuarioPermissionService;

    @GetMapping
    public List<EquipeUsuarioResponse> listar(Authentication authentication) {
        Clinica clinica = clinicaAutenticada(authentication);
        return adminUsuarioService.listar(clinica);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipeUsuarioResponse criarUsuario(
            @RequestBody @Valid EquipeUsuarioCreateRequest request,
            Authentication authentication
    ) {
        Clinica clinica = clinicaAutenticada(authentication);
        return adminUsuarioService.criarUsuario(clinica, request);
    }

    @PatchMapping("/{id}/status")
    public EquipeUsuarioResponse alterarStatus(
            @PathVariable Long id,
            @RequestBody @Valid StatusRequest request,
            Authentication authentication
    ) {
        Clinica clinica = clinicaAutenticada(authentication);
        return adminUsuarioService.alterarStatus(clinica, id, request);
    }

    @PatchMapping("/{id}/resetar-senha")
    public EquipeUsuarioResponse resetarSenha(
            @PathVariable Long id,
            @RequestBody @Valid ResetPasswordRequest request,
            Authentication authentication
    ) {
        Clinica clinica = clinicaAutenticada(authentication);
        return adminUsuarioService.resetarSenha(clinica, id, request);
    }

    private Clinica clinicaAutenticada(Authentication authentication) {
        Usuario usuario = usuarioPermissionService.exigirGerenciador(authentication);
        return usuario.getClinica();
    }
}
