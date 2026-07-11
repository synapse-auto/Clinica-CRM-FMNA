package com.synapse.clinicafemina.dto.auth;

import com.synapse.clinicafemina.domain.Usuario;

public record AuthUserResponse(
        Long id,
        String nome,
        String email,
        String perfil,
        Long clinicaId,
        Boolean mustChangePassword,
        Boolean podeGerenciarUsuarios
) {
    public static AuthUserResponse from(Usuario usuario) {
        return new AuthUserResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getClinica().getId(),
                usuario.getMustChangePassword(),
                usuario.getPodeGerenciarUsuarios()
        );
    }
}
