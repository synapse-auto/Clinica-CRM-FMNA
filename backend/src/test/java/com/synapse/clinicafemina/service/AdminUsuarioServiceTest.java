package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioCreateRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.dto.auth.ResetPasswordRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.cache.ClinicDataChangePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ClinicDataChangePublisher clinicDataChangePublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private AdminUsuarioService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new AdminUsuarioService(usuarioRepository, passwordEncoder, clinicDataChangePublisher);
        clinica = new Clinica();
        clinica.setId(10L);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void should_list_all_users_of_current_clinic() {
        Gestor gestor = usuario(new Gestor(), 1L, "Gestor", "gestor@clinica.local", "GESTOR", 10L);
        Medico medico = usuario(new Medico(), 2L, "Medico", "medico@clinica.local", "MEDICO", 10L);
        medico.setAtivo(false);

        when(usuarioRepository.findTodosByClinicaId(10L)).thenReturn(List.of(gestor, medico));

        List<EquipeUsuarioResponse> response = service.listar(clinica);

        assertEquals(2, response.size());
        assertEquals("Gestor", response.get(0).nome());
        assertTrue(response.get(0).ativo());
        assertEquals("Medico", response.get(1).nome());
        assertFalse(response.get(1).ativo());
    }

    @Test
    void should_create_user_with_first_login_required() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Novo Atendente",
                "novo.atendente@clinica.local",
                "RECEPCIONISTA",
                "44999998888",
                " Senha@123 "
        );

        when(usuarioRepository.findByEmail("novo.atendente@clinica.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(" Senha@123 ")).thenReturn("encodedPassword");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario saved = inv.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        EquipeUsuarioResponse response = service.criarUsuario(clinica, request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario saved = captor.getValue();

        assertInstanceOf(Recepcionista.class, saved);
        assertEquals(clinica, saved.getClinica());
        assertEquals("Novo Atendente", saved.getNome());
        assertEquals("novo.atendente@clinica.local", saved.getEmail());
        assertEquals("encodedPassword", saved.getSenhaHash());
        assertTrue(saved.getAtivo());
        assertTrue(saved.getMustChangePassword());
        assertFalse(saved.getPodeGerenciarUsuarios());
        verify(passwordEncoder).encode(" Senha@123 ");
        verify(clinicDataChangePublisher).publish(10L);
    }

    @Test
    void should_reject_duplicate_email() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Duplicado",
                "dup@clinica.local",
                "MEDICO",
                null,
                "Medico1"
        );

        when(usuarioRepository.findByEmail("dup@clinica.local")).thenReturn(Optional.of(new Medico()));

        assertThrows(BadRequestException.class, () -> service.criarUsuario(clinica, request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_reject_weak_password() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Senha Fraca",
                "fraco@clinica.local",
                "GESTOR",
                null,
                "123"
        );

        assertThrows(BadRequestException.class, () -> service.criarUsuario(clinica, request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_alter_user_status_with_clinic_isolation() {
        Gestor gestor = usuario(new Gestor(), 1L, "Gestor", "gestor@clinica.local", "GESTOR", 10L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(gestor));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(gestor);

        // Mock current authenticated user to be different from 1L to avoid self-deactivation check
        Gestor current = usuario(new Gestor(), 99L, "Current", "curr@clinica.local", "GESTOR", 10L);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(current);

        EquipeUsuarioResponse response = service.alterarStatus(clinica, 1L, new StatusRequest(false));

        assertFalse(gestor.getAtivo());
        assertFalse(response.ativo());
        verify(clinicDataChangePublisher).publish(10L);
    }

    @Test
    void should_reject_status_alteration_for_different_clinic() {
        Gestor gestor = usuario(new Gestor(), 1L, "Gestor", "gestor@clinica.local", "GESTOR", 20L); // Clinica 20L
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(gestor));

        assertThrows(BadRequestException.class, () -> service.alterarStatus(clinica, 1L, new StatusRequest(false))); // Current clinica is 10L
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_prevent_self_deactivation() {
        Gestor gestor = usuario(new Gestor(), 1L, "Gestor", "gestor@clinica.local", "GESTOR", 10L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(gestor));

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(gestor);

        assertThrows(BadRequestException.class, () -> service.alterarStatus(clinica, 1L, new StatusRequest(false)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_reset_user_password_temporarily() {
        Medico medico = usuario(new Medico(), 2L, "Medico", "medico@clinica.local", "MEDICO", 10L);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico));
        when(passwordEncoder.encode("Medico@Temp123")).thenReturn("newEncodedPassword");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(medico);

        EquipeUsuarioResponse response = service.resetarSenha(clinica, 2L, new ResetPasswordRequest("Medico@Temp123"));

        assertEquals("newEncodedPassword", medico.getSenhaHash());
        assertTrue(medico.getMustChangePassword());
        assertTrue(response.mustChangePassword());
        verify(passwordEncoder).encode("Medico@Temp123");
        verify(clinicDataChangePublisher, never()).publish(any());
    }

    private <T extends Usuario> T usuario(T usuario, Long id, String nome, String email, String perfil, Long clinicaId) {
        Clinica c = new Clinica();
        c.setId(clinicaId);
        usuario.setId(id);
        usuario.setClinica(c);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setMustChangePassword(false);
        usuario.setAdminInterno(false);
        return usuario;
    }
}
