package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.dto.auth.ChangePasswordRequest;
import com.synapse.clinicafemina.dto.auth.LoginRequest;
import com.synapse.clinicafemina.dto.auth.LoginResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private Authentication authentication;

    private AuthService service;
    private Gestor usuario;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                authenticationManager,
                jwtService,
                passwordEncoder,
                usuarioRepository
        );
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        usuario = new Gestor();
        usuario.setId(3L);
        usuario.setClinica(clinica);
        usuario.setNome("Gestora");
        usuario.setEmail("gestora@clinica.local");
        usuario.setPerfil("GESTOR");
        usuario.setSenhaHash("$2a$12$old");
        usuario.setMustChangePassword(true);
    }

    @Test
    void should_authenticate_and_return_first_access_state() {
        LoginRequest request = new LoginRequest();
        request.setEmail(usuario.getEmail());
        request.setSenha("SenhaInicial!2026");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(usuario);
        when(jwtService.generateToken(anyMap(), any())).thenReturn("jwt");

        LoginResponse response = service.login(request);

        assertEquals("jwt", response.getToken());
        assertTrue(response.getMustChangePassword());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void should_change_password_with_bcrypt_and_clear_first_access_state() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "SenhaInicial!2026",
                "Senha@123",
                "Senha@123"
        );
        when(passwordEncoder.matches("SenhaInicial!2026", "$2a$12$old")).thenReturn(true);
        when(passwordEncoder.matches("Senha@123", "$2a$12$old")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("$2a$12$new");
        when(jwtService.generateToken(anyMap(), any())).thenReturn("new-jwt");

        LoginResponse response = service.changePassword(usuario, request);

        assertEquals("$2a$12$new", usuario.getSenhaHash());
        assertFalse(usuario.getMustChangePassword());
        assertEquals("new-jwt", response.getToken());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void should_reject_password_change_when_confirmation_differs() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "SenhaInicial!2026",
                "Lucas123",
                "Atendente1"
        );
        when(passwordEncoder.matches("SenhaInicial!2026", "$2a$12$old")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.changePassword(usuario, request));
    }

    @Test
    void should_reject_weak_new_password() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "SenhaInicial!2026",
                "abcdef",
                "abcdef"
        );
        when(passwordEncoder.matches("SenhaInicial!2026", "$2a$12$old")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.changePassword(usuario, request));
    }

    @Test
    void should_reject_numeric_only_password() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "SenhaInicial!2026",
                "12345678",
                "12345678"
        );
        when(passwordEncoder.matches("SenhaInicial!2026", "$2a$12$old")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.changePassword(usuario, request));
    }

    @Test
    void should_accept_six_or_more_alphanumeric_password_with_letter_and_number() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "SenhaInicial!2026",
                "Ultra123",
                "Ultra123"
        );
        when(passwordEncoder.matches("SenhaInicial!2026", "$2a$12$old")).thenReturn(true);
        when(passwordEncoder.matches("Ultra123", "$2a$12$old")).thenReturn(false);
        when(passwordEncoder.encode("Ultra123")).thenReturn("$2a$12$new");
        when(jwtService.generateToken(anyMap(), any())).thenReturn("new-jwt");

        LoginResponse response = service.changePassword(usuario, request);

        assertEquals("$2a$12$new", usuario.getSenhaHash());
        assertFalse(usuario.getMustChangePassword());
        assertEquals("new-jwt", response.getToken());
    }
}
