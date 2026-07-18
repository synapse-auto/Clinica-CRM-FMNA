package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioPermissionServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private Authentication authentication;

    private UsuarioPermissionService service;
    private Gestor principal;

    @BeforeEach
    void setUp() {
        service = new UsuarioPermissionService(usuarioRepository);
        principal = gestor(1L, true);
    }

    @Test
    void should_read_latest_permission_from_database() {
        Gestor current = gestor(1L, true);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(current));

        assertTrue(service.podeGerenciarUsuarios(authentication));
        assertSame(current, service.exigirGerenciador(authentication));
    }

    @Test
    void should_reject_revoked_inactive_deleted_or_non_manager_user() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);

        Gestor revoked = gestor(1L, false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(revoked));
        assertFalse(service.podeGerenciarUsuarios(authentication));

        Gestor inactive = gestor(1L, true);
        inactive.setAtivo(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(inactive));
        assertThrows(AccessDeniedException.class, () -> service.exigirGerenciador(authentication));

        Gestor deleted = gestor(1L, true);
        deleted.setDeletadoEm(OffsetDateTime.now());
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(deleted));
        assertFalse(service.podeGerenciarUsuarios(authentication));

        Medico medico = base(new Medico(), 1L, "MEDICO");
        medico.setPodeGerenciarUsuarios(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));
        assertFalse(service.podeGerenciarUsuarios(authentication));
    }

    @Test
    void should_reject_missing_or_non_entity_principal() {
        assertFalse(service.podeGerenciarUsuarios(null));

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymous");
        assertFalse(service.podeGerenciarUsuarios(authentication));
    }

    private Gestor gestor(Long id, boolean permission) {
        Gestor gestor = base(new Gestor(), id, "GESTOR");
        gestor.setPodeGerenciarUsuarios(permission);
        return gestor;
    }

    private <T extends Usuario> T base(T usuario, Long id, String profile) {
        Clinica clinic = new Clinica();
        clinic.setId(7L);
        usuario.setId(id);
        usuario.setClinica(clinic);
        usuario.setPerfil(profile);
        usuario.setAtivo(true);
        usuario.setDeletadoEm(null);
        return usuario;
    }
}
