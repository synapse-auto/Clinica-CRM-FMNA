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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("@usuarioPermissionService.podeGerenciarUsuarios(authentication)")
@Tag(name = "Equipe", description = "Administração de usuários por quem possui permissão de gerenciamento.")
@SecurityRequirement(name = "bearerAuth")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;
    private final UsuarioPermissionService usuarioPermissionService;

    @GetMapping
    @Operation(summary = "Listar usuários administrativos")
    public List<EquipeUsuarioResponse> listar(Authentication authentication) {
        Clinica clinica = clinicaAutenticada(authentication);
        return adminUsuarioService.listar(clinica);
    }

    @PostMapping
    @Operation(summary = "Criar usuário administrativo")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipeUsuarioResponse criarUsuario(
            @RequestBody @Valid EquipeUsuarioCreateRequest request,
            Authentication authentication
    ) {
        Clinica clinica = clinicaAutenticada(authentication);
        return adminUsuarioService.criarUsuario(clinica, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Ativar ou desativar usuário")
    public EquipeUsuarioResponse alterarStatus(
            @PathVariable Long id,
            @RequestBody @Valid StatusRequest request,
            Authentication authentication
    ) {
        Clinica clinica = clinicaAutenticada(authentication);
        return adminUsuarioService.alterarStatus(clinica, id, request);
    }

    @PatchMapping("/{id}/resetar-senha")
    @Operation(summary = "Redefinir senha temporária")
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
