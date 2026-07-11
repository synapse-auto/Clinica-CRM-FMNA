package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioPermissionService {

    private final UsuarioRepository usuarioRepository;
    private final ClinicaConfigService clinicaConfigService;

    public boolean podeGerenciarUsuarios(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Usuario usuario)) {
            return false;
        }

        // Buscar do banco de dados para garantir que a permissão é a mais recente
        Long clinicaAtualId = clinicaConfigService.obterClinicaAtual().getId();
        return usuarioRepository.findById(usuario.getId())
                .map(u -> "GESTOR".equals(u.getPerfil())
                        && Boolean.TRUE.equals(u.getPodeGerenciarUsuarios())
                        && u.getClinica() != null
                        && clinicaAtualId.equals(u.getClinica().getId()))
                .orElse(false);
    }
}
