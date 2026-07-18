package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.AlterarNomeUsuarioRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.audit.UsuarioNomeAuditEvent;
import com.synapse.clinicafemina.service.cache.ClinicDataChangePublisher;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipeNameServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ClinicaRepository clinicaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ClinicDataChangePublisher clinicDataChangePublisher;
    @Mock private UsuarioPermissionService usuarioPermissionService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private Authentication authentication;

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
        executor = new Gestor();
        executor.setId(1L);
        executor.setClinica(clinica);
        when(usuarioPermissionService.exigirGerenciador(authentication)).thenReturn(executor);
    }

    @Test
    void should_normalize_and_update_unicode_name_without_changing_other_fields() {
        Recepcionista target = targetUser();
        String originalEmail = target.getEmail();
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(10L, 7L)).thenReturn(Optional.of(target));
        when(usuarioRepository.save(target)).thenReturn(target);

        var response = service.alterarNome(
                10L,
                new AlterarNomeUsuarioRequest("  Ana   D'Ávila-Souza  "),
                authentication
        );

        assertEquals("Ana D'Ávila-Souza", response.nome());
        assertEquals(originalEmail, target.getEmail());
        verify(clinicDataChangePublisher).publish(7L);
        ArgumentCaptor<UsuarioNomeAuditEvent> event = ArgumentCaptor.forClass(UsuarioNomeAuditEvent.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        assertEquals("nome", event.getValue().campo());
        assertEquals(1L, event.getValue().executorId());
        assertEquals(10L, event.getValue().usuarioAlvoId());
        assertEquals(7L, event.getValue().clinicaId());
    }

    @Test
    void should_hide_users_from_another_clinic() {
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(99L, 7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.alterarNome(
                99L,
                new AlterarNomeUsuarioRequest("Nome Válido"),
                authentication
        ));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_reject_internal_inactive_and_deleted_targets() {
        Recepcionista internal = targetUser();
        internal.setAdminInterno(true);
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(10L, 7L)).thenReturn(Optional.of(internal));
        assertThrows(AccessDeniedException.class, () -> service.alterarNome(
                10L, new AlterarNomeUsuarioRequest("Nome Válido"), authentication
        ));

        Recepcionista inactive = targetUser();
        inactive.setAtivo(false);
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(11L, 7L)).thenReturn(Optional.of(inactive));
        assertThrows(BadRequestException.class, () -> service.alterarNome(
                11L, new AlterarNomeUsuarioRequest("Nome Válido"), authentication
        ));

        Recepcionista deleted = targetUser();
        deleted.setDeletadoEm(java.time.OffsetDateTime.now());
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(12L, 7L)).thenReturn(Optional.of(deleted));
        assertThrows(BadRequestException.class, () -> service.alterarNome(
                12L, new AlterarNomeUsuarioRequest("Nome Válido"), authentication
        ));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_reject_blank_short_long_and_control_characters() {
        Recepcionista target = targetUser();
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(any(), any())).thenReturn(Optional.of(target));

        assertThrows(BadRequestException.class, () -> service.alterarNome(
                10L, new AlterarNomeUsuarioRequest(" "), authentication
        ));
        assertThrows(BadRequestException.class, () -> service.alterarNome(
                10L, new AlterarNomeUsuarioRequest("A"), authentication
        ));
        assertThrows(BadRequestException.class, () -> service.alterarNome(
                10L, new AlterarNomeUsuarioRequest("A".repeat(201)), authentication
        ));
        assertThrows(BadRequestException.class, () -> service.alterarNome(
                10L, new AlterarNomeUsuarioRequest("Nome\nInjetado"), authentication
        ));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_not_publish_or_save_when_normalized_name_is_unchanged() {
        Recepcionista target = targetUser();
        target.setNome("Nome Atual");
        when(usuarioRepository.findByIdAndClinicaIdForUpdate(10L, 7L)).thenReturn(Optional.of(target));

        var response = service.alterarNome(
                10L,
                new AlterarNomeUsuarioRequest(" Nome   Atual "),
                authentication
        );

        assertEquals("Nome Atual", response.nome());
        verify(usuarioRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
        verify(clinicDataChangePublisher, never()).publish(any());
    }

    private Recepcionista targetUser() {
        Recepcionista user = new Recepcionista();
        user.setId(10L);
        user.setClinica(clinica);
        user.setNome("Nome Atual");
        user.setEmail("usuario@clinica.local");
        user.setPerfil("RECEPCIONISTA");
        user.setAtivo(true);
        user.setAdminInterno(false);
        user.setMustChangePassword(false);
        return user;
    }
}
