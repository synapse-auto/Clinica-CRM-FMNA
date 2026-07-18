package com.synapse.clinicafemina.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private ClinicaRepository clinicaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_create_initial_users_from_environment_with_bcrypt_and_required_password_change() {
        Clinica clinica = clinic();
        DataSeeder seeder = seeder(true, """
                [
                  {
                    "nome": "Atendente Inicial",
                    "email": "atendente@local.test",
                    "perfil": "RECEPCIONISTA",
                    "password": "Senha@Inicial2026",
                    "mustChangePassword": true
                  },
                  {
                    "nome": "Admin Interno",
                    "email": "admin@local.test",
                    "perfil": "GESTOR",
                    "password": "OutraSenha2026",
                    "mustChangePassword": true,
                    "adminInterno": true
                  }
                ]
                """);

        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");

        seeder.run();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(2)).save(captor.capture());
        List<Usuario> users = captor.getAllValues();

        assertInstanceOf(Recepcionista.class, users.get(0));
        assertInstanceOf(Gestor.class, users.get(1));
        assertTrue(users.get(0).getMustChangePassword());
        assertFalse(users.get(0).getAdminInterno());
        assertTrue(users.get(1).getAdminInterno());
        assertNotEquals("Senha@Inicial2026", users.get(0).getSenhaHash());
        assertEquals("$2a$12$hash", users.get(0).getSenhaHash());
        verify(passwordEncoder).encode("Senha@Inicial2026");
    }

    @Test
    void should_not_overwrite_existing_password_without_explicit_reset() {
        Clinica clinica = clinic();
        Gestor existing = new Gestor();
        existing.setSenhaHash("$2a$12$existing");
        DataSeeder seeder = seeder(true, """
                [{
                  "nome": "Gestor",
                  "email": "gestor@local.test",
                  "perfil": "GESTOR",
                  "password": "NovaSenha2026",
                  "mustChangePassword": true
                }]
                """);

        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByEmail("gestor@local.test")).thenReturn(Optional.of(existing));

        seeder.run();

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any());
        assertEquals("$2a$12$existing", existing.getSenhaHash());
    }

    @Test
    void should_not_overwrite_existing_name_after_restart() {
        Clinica clinica = clinic();
        Gestor existing = new Gestor();
        existing.setNome("Nome Alterado no CRM");
        existing.setSenhaHash("$2a$12$existing");
        DataSeeder seeder = seeder(true, """
                [{
                  "nome": "Nome Antigo do Seeder",
                  "email": "gestor@local.test",
                  "perfil": "GESTOR",
                  "password": "NovaSenha2026"
                }]
                """);

        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByEmail("gestor@local.test")).thenReturn(Optional.of(existing));

        seeder.run();

        assertEquals("Nome Alterado no CRM", existing.getNome());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_preserve_existing_user_management_permission_after_restart() {
        Clinica clinica = clinic();
        Gestor existing = new Gestor();
        existing.setSenhaHash("$2a$12$existing");
        existing.setPodeGerenciarUsuarios(true);
        DataSeeder seeder = seeder(true, """
                [{
                  "nome": "Gestor Existente",
                  "email": "gestor.existente@local.test",
                  "perfil": "GESTOR",
                  "password": "SenhaTemporaria1"
                }]
                """);

        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByEmail("gestor.existente@local.test")).thenReturn(Optional.of(existing));

        seeder.run();

        assertTrue(existing.getPodeGerenciarUsuarios());
        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void should_reset_existing_password_only_when_json_explicitly_requests_it() {
        Clinica clinica = clinic();
        Gestor existing = new Gestor();
        existing.setSenhaHash("$2a$12$existing");
        existing.setMustChangePassword(false);
        DataSeeder seeder = seeder(true, """
                [{
                  "nome": "Gestor",
                  "email": "gestor@local.test",
                  "perfil": "GESTOR",
                  "password": "NovaSenha2026",
                  "mustChangePassword": true,
                  "resetPassword": true
                }]
                """);

        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByEmail("gestor@local.test")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("NovaSenha2026")).thenReturn("$2a$12$new");
        ReflectionTestUtils.setField(seeder, "allowPasswordReset", true);

        seeder.run();

        verify(usuarioRepository).save(existing);
        assertEquals("$2a$12$new", existing.getSenhaHash());
        assertTrue(existing.getMustChangePassword());
    }

    @Test
    void should_block_existing_password_reset_in_production_even_with_opt_in() {
        Clinica clinica = clinic();
        Gestor existing = new Gestor();
        existing.setSenhaHash("$2a$12$existing");
        existing.setMustChangePassword(false);
        DataSeeder seeder = seeder(true, """
                [{
                  "nome": "Gestor",
                  "email": "gestor@local.test",
                  "perfil": "GESTOR",
                  "password": "NovaSenha2026",
                  "resetPassword": true
                }]
                """);
        ReflectionTestUtils.setField(seeder, "allowPasswordReset", true);
        ReflectionTestUtils.setField(seeder, "applicationEnvironment", "producao");

        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByEmail("gestor@local.test")).thenReturn(Optional.of(existing));

        seeder.run();

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any());
        assertEquals("$2a$12$existing", existing.getSenhaHash());
        assertFalse(existing.getMustChangePassword());
    }

    @Test
    void should_require_explicit_opt_in_before_resetting_existing_password_outside_production() {
        Clinica clinica = clinic();
        Gestor existing = new Gestor();
        existing.setSenhaHash("$2a$12$existing");
        DataSeeder seeder = seeder(true, """
                [{
                  "nome": "Gestor",
                  "email": "gestor@local.test",
                  "perfil": "GESTOR",
                  "password": "NovaSenha2026",
                  "resetPassword": true
                }]
                """);

        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(usuarioRepository.findByEmail("gestor@local.test")).thenReturn(Optional.of(existing));

        seeder.run();

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any());
        assertEquals("$2a$12$existing", existing.getSenhaHash());
    }

    @Test
    void should_not_seed_when_initial_users_are_disabled() {
        DataSeeder seeder = seeder(false, "[]");

        seeder.run();

        verify(clinicaRepository, never()).findBySlug(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_explain_required_format_when_initial_users_json_is_invalid() {
        DataSeeder seeder = seeder(true, "[{\"email\":]");
        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinic()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                seeder::run
        );

        assertEquals(
                "INITIAL_USERS_JSON possui formato inválido. Use JSON puro, sem Markdown ou mailto.",
                exception.getMessage()
        );
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_reject_markdown_email_before_creating_initial_user() {
        DataSeeder seeder = seeder(true, """
                [{
                  "nome": "Admin Interno",
                  "email": "[admin@local.test](mailto:admin@local.test)",
                  "perfil": "GESTOR",
                  "password": "SenhaInicial2026",
                  "adminInterno": true
                }]
                """);
        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinic()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                seeder::run
        );

        assertEquals("Email inválido em INITIAL_USERS_JSON", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    private DataSeeder seeder(boolean enabled, String json) {
        DataSeeder seeder = new DataSeeder(
                clinicaRepository,
                usuarioRepository,
                passwordEncoder,
                objectMapper
        );
        ReflectionTestUtils.setField(seeder, "initialUsersEnabled", enabled);
        ReflectionTestUtils.setField(seeder, "initialUsersJson", json);
        ReflectionTestUtils.setField(seeder, "clinicSlug", "fmna");
        ReflectionTestUtils.setField(seeder, "applicationEnvironment", "teste");
        ReflectionTestUtils.setField(seeder, "allowPasswordReset", false);
        return seeder;
    }

    private Clinica clinic() {
        Clinica clinica = new Clinica();
        clinica.setNome("Clínica Teste");
        clinica.setSlug("fmna");
        return clinica;
    }
}
