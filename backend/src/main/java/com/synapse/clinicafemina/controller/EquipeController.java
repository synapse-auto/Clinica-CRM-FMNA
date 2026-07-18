package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Usuario;
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

@RestController
@RequestMapping("/api/equipe")
@RequiredArgsConstructor
@PreAuthorize("@usuarioPermissionService.podeGerenciarUsuarios(authentication)")
public class EquipeController {

    private final EquipeService equipeService;
    private final UsuarioPermissionService usuarioPermissionService;

    @GetMapping
    public EquipeResponse listar(Authentication authentication) {
        Clinica clinica = clinicaAutenticada(authentication);
        return equipeService.listar(clinica);
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipeUsuarioResponse criarUsuario(
            @RequestBody @Valid EquipeUsuarioCreateRequest request,
            Authentication authentication
    ) {
        Clinica clinica = clinicaAutenticada(authentication);
        return equipeService.criarUsuario(clinica, request);
    }

    @PatchMapping("/usuarios/{usuarioId}/permissao-gerenciamento")
    public EquipeUsuarioResponse alterarPermissaoGerenciamento(
            @PathVariable Long usuarioId,
            @RequestBody @Valid PermissaoGerenciamentoRequest request,
            Authentication authentication
    ) {
        return equipeService.alterarPermissaoGerenciamento(usuarioId, request, authentication);
    }

    private Clinica clinicaAutenticada(Authentication authentication) {
        Usuario usuario = usuarioPermissionService.exigirGerenciador(authentication);
        return usuario.getClinica();
    }
}
