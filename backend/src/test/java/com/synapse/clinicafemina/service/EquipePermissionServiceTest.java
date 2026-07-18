package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.dto.equipe.PermissaoGerenciamentoRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.audit.UsuarioPermissionAuditEvent;
import com.synapse.clinicafemina.service.cache.ClinicDataChangePublisher;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipePermissionServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ClinicaRepository clinicaRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ClinicDataChangePublisher clinicDataChangePublisher;
    @Mock
    private UsuarioPermissionService usuarioPermissionService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private Authentication authentication;

    private EquipeService service;
    private Clinica clinica;
    private Gestor executor;

    @BeforeEach
    void setUp() {
        service = new EquipeService(
                usuarioRepository,
                clinicaRepository,
                passwordEncoder,
                clinicDataChangePublisher,
                usuarioPermissionService,
                applicationEventPublisher
        );
        clinica = new Clinica();
        clinica.setId(7L);
        executor = gestor(1L, true);
    }

    @Test
    void should_grant_permission_to_active_manager_of_same_clinic() {
        Gestor target = gestor(2L, false);
        prepare(target);
        when(usuarioRepository.save(target)).thenReturn(target);

        EquipeUsuarioResponse response = service.alterarPermissaoGerenciamento(
                2L,
                new PermissaoGerenciamentoRequest(true),
                authentication
        );

        assertTrue(target.getPodeGerenciarUsuarios());
        assertTrue(response.podeGerenciarUsuarios());
        ArgumentCaptor<UsuarioPermissionAuditEvent> eventCaptor =
                ArgumentCaptor.forClass(UsuarioPermissionAuditEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().executorId());
        assertEquals(2L, eventCaptor.getValue().usuarioAlvoId());
        assertEquals(7L, eventCaptor.getValue().clinicaId());
        assertFalse(eventCaptor.getValue().valorAnterior());
        assertTrue(eventCaptor.getValue().valorNovo());
        verify(clinicDataChangePublisher, never()).publish(any());
    }

    @Test
    void should_revoke_permission_when_another_authorized_manager_exists() {
        Gestor target = gestor(2L, true);
        prepare(target);
        when(usuarioRepository.countGestoresAutorizadosAtivosByClinicaId(7L)).thenReturn(2L);
        when(usuarioRepository.save(target)).thenReturn(target);

        EquipeUsuarioResponse response = service.alterarPermissaoGerenciamento(
                2L,
                new PermissaoGerenciamentoRequest(false),
                authentication
        );

        assertFalse(target.getPodeGerenciarUsuarios());
        assertFalse(response.podeGerenciarUsuarios());
    }

    @Test
    void should_allow_self_revoke_when_another_authorized_manager_exists() {
        prepare(executor);
        when(usuarioRepository.countGestoresAutorizadosAtivosByClinicaId(7L)).thenReturn(2L);
        when(usuarioRepository.save(executor)).thenReturn(executor);

        service.alterarPermissaoGerenciamento(
                1L,
                new PermissaoGerenciamentoRequest(false),
                authentication
        );

        assertFalse(executor.getPodeGerenciarUsuarios());
    }

    @Test
    void should_reject_revoking_last_authorized_manager() {
        prepare(executor);
        when(usuarioRepository.countGestoresAutorizadosAtivosByClinicaId(7L)).thenReturn(1L);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.alterarPermissaoGerenciamento(
                        1L,
                        new PermissaoGerenciamentoRequest(false),
                        authentication
                )
        );

        assertEquals("A clínica precisa manter ao menos um gestor com essa permissão.", exception.getMessage());
        assertTrue(executor.getPodeGerenciarUsuarios());
        verify(usuarioRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_reject_non_manager_internal_inactive_and_deleted_targets() {
        Medico medico = base(new Medico(), 2L, "MEDICO");
        prepare(medico);
        assertThrows(BadRequestException.class, () -> change(2L, true));

        Gestor internal = gestor(3L, false);
        internal.setAdminInterno(true);
        prepare(internal);
        assertThrows(AccessDeniedException.class, () -> change(3L, true));

        Gestor inactive = gestor(4L, false);
        inactive.setAtivo(false);
        prepare(inactive);
        assertThrows(BadRequestException.class, () -> change(4L, true));

        Gestor deleted = gestor(5L, false);
        deleted.setDeletadoEm(OffsetDateTime.now());
        prepare(deleted);
        assertThrows(BadRequestException.class, () -> change(5L, true));

        verify(usuarioRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_not_reveal_target_from_another_clinic() {
        when(usuarioPermissionService.exigirGerenciador(authentication)).thenReturn(executor);
        when(clinicaRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(99L, 7L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> change(99L, true));

        assertEquals("Usuário não encontrado.", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_revalidate_requester_in_service() {
        when(usuarioPermissionService.exigirGerenciador(authentication))
                .thenThrow(new AccessDeniedException("Acesso negado."));

        assertThrows(AccessDeniedException.class, () -> change(2L, true));
        verify(clinicaRepository, never()).findByIdForUpdate(any());
    }

    private EquipeUsuarioResponse change(Long targetId, boolean enabled) {
        return service.alterarPermissaoGerenciamento(
                targetId,
                new PermissaoGerenciamentoRequest(enabled),
                authentication
        );
    }

    private void prepare(Usuario target) {
        when(usuarioPermissionService.exigirGerenciador(authentication)).thenReturn(executor);
        when(clinicaRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(target.getId(), 7L))
                .thenReturn(Optional.of(target));
    }

    private Gestor gestor(Long id, boolean permission) {
        Gestor gestor = base(new Gestor(), id, "GESTOR");
        gestor.setPodeGerenciarUsuarios(permission);
        return gestor;
    }

    private <T extends Usuario> T base(T usuario, Long id, String profile) {
        usuario.setId(id);
        usuario.setClinica(clinica);
        usuario.setNome("Usuário " + id);
        usuario.setEmail("usuario" + id + "@clinica.test");
        usuario.setPerfil(profile);
        usuario.setAtivo(true);
        usuario.setAdminInterno(false);
        usuario.setMustChangePassword(false);
        usuario.setPodeGerenciarUsuarios(false);
        return usuario;
    }
}
