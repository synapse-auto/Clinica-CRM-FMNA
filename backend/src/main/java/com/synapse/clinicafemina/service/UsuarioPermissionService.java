package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioPermissionService {

    private static final String ACCESS_DENIED_MESSAGE = "Usuário não autorizado a gerenciar a equipe.";
    private static final String ADMIN_INTERNO_ACCESS_DENIED_MESSAGE = "Acesso restrito a administradores internos.";

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public boolean podeGerenciarUsuarios(Authentication authentication) {
        try {
            exigirGerenciador(authentication);
            return true;
        } catch (AccessDeniedException exception) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Usuario exigirGerenciador(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Usuario usuario) || usuario.getId() == null) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }

        Usuario atual = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MESSAGE));
        if (!Boolean.TRUE.equals(atual.getAtivo())
                || atual.getDeletadoEm() != null
                || !"GESTOR".equals(atual.getPerfil())
                || !Boolean.TRUE.equals(atual.getPodeGerenciarUsuarios())
                || atual.getClinica() == null) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }
        return atual;
    }

    @Transactional(readOnly = true)
    public boolean podeDiagnosticarFotoUazap(Authentication authentication) {
        try {
            exigirAdminInterno(authentication);
            return true;
        } catch (AccessDeniedException exception) {
            return false;
        }
    }

    /** Exige GESTOR + adminInterno=true — usado exclusivamente pelo diagnóstico de foto UAZAP. */
    @Transactional(readOnly = true)
    public Usuario exigirAdminInterno(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(ADMIN_INTERNO_ACCESS_DENIED_MESSAGE);
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Usuario usuario) || usuario.getId() == null) {
            throw new AccessDeniedException(ADMIN_INTERNO_ACCESS_DENIED_MESSAGE);
        }

        Usuario atual = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new AccessDeniedException(ADMIN_INTERNO_ACCESS_DENIED_MESSAGE));
        if (!Boolean.TRUE.equals(atual.getAtivo())
                || atual.getDeletadoEm() != null
                || !"GESTOR".equals(atual.getPerfil())
                || !Boolean.TRUE.equals(atual.getAdminInterno())
                || atual.getClinica() == null) {
            throw new AccessDeniedException(ADMIN_INTERNO_ACCESS_DENIED_MESSAGE);
        }
        return atual;
    }
}
