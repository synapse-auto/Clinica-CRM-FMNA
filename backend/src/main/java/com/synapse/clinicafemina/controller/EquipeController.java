package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.AlterarNomeUsuarioRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeResponse;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioCreateRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.dto.equipe.PermissaoGerenciamentoRequest;
import com.synapse.clinicafemina.service.EquipeService;
import com.synapse.clinicafemina.service.UsuarioPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/equipe")
@RequiredArgsConstructor
@PreAuthorize("@usuarioPermissionService.podeGerenciarUsuarios(authentication)")
@Tag(name = "Equipe", description = "Gestão de usuários limitada a quem possui permissão de gerenciamento.")
@SecurityRequirement(name = "bearerAuth")
public class EquipeController {

    private final EquipeService equipeService;
    private final UsuarioPermissionService usuarioPermissionService;

    @GetMapping
    @Operation(summary = "Listar equipe")
    public EquipeResponse listar(Authentication authentication) {
        Clinica clinica = clinicaAutenticada(authentication);
        return equipeService.listar(clinica);
    }

    @PostMapping("/usuarios")
    @Operation(summary = "Criar usuário da equipe")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipeUsuarioResponse criarUsuario(
            @RequestBody @Valid EquipeUsuarioCreateRequest request,
            Authentication authentication
    ) {
        Clinica clinica = clinicaAutenticada(authentication);
        return equipeService.criarUsuario(clinica, request);
    }

    @PatchMapping("/usuarios/{usuarioId}/permissao-gerenciamento")
    @Operation(summary = "Alterar permissão de gerenciamento")
    public EquipeUsuarioResponse alterarPermissaoGerenciamento(
            @PathVariable Long usuarioId,
            @RequestBody @Valid PermissaoGerenciamentoRequest request,
            Authentication authentication
    ) {
        return equipeService.alterarPermissaoGerenciamento(usuarioId, request, authentication);
    }

    @PatchMapping("/usuarios/{usuarioId}/nome")
    public EquipeUsuarioResponse alterarNome(
            @PathVariable Long usuarioId,
            @RequestBody AlterarNomeUsuarioRequest request,
            Authentication authentication
    ) {
        return equipeService.alterarNome(usuarioId, request, authentication);
    }

    private Clinica clinicaAutenticada(Authentication authentication) {
        Usuario usuario = usuarioPermissionService.exigirGerenciador(authentication);
        return usuario.getClinica();
    }
}
