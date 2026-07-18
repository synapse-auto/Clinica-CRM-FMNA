package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.EquipeGrupoResponse;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioCreateRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.cache.ClinicDataChangePublisher;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipeServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ClinicDataChangePublisher clinicDataChangePublisher;

    private EquipeService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new EquipeService(usuarioRepository, passwordEncoder, clinicDataChangePublisher);
        clinica = new Clinica();
        clinica.setId(7L);
    }

    @Test
    void should_list_visible_users_grouped_by_profile() {
        Gestor gestor = usuario(new Gestor(), 1L, "Gestora Real", "gestora@clinica.local", "GESTOR");
        Medico medico = usuario(new Medico(), 2L, "Medico Real", "medico@clinica.local", "MEDICO");
        Recepcionista recepcionista = usuario(new Recepcionista(), 3L, "Recepcao Real", "recepcao@clinica.local", "RECEPCIONISTA");
        when(usuarioRepository.findAtivosVisiveisByClinicaId(7L))
                .thenReturn(List.of(recepcionista, medico, gestor));

        var response = service.listar(clinica);

        assertEquals(List.of("GESTOR", "MEDICO", "RECEPCIONISTA"),
                response.grupos().stream().map(EquipeGrupoResponse::perfil).toList());
        assertEquals("Gestores", response.grupos().get(0).titulo());
        assertEquals("M\u00e9dicos", response.grupos().get(1).titulo());
        assertEquals("Recepcionistas", response.grupos().get(2).titulo());
        assertEquals(List.of("Gestora Real"), nomes(response.grupos().get(0)));
        assertEquals(List.of("Medico Real"), nomes(response.grupos().get(1)));
        assertEquals(List.of("Recepcao Real"), nomes(response.grupos().get(2)));
    }

    @Test
    void should_create_receptionist_with_temporary_password_and_first_login_required() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Recepcao Nova",
                " Recepcao.Nova@clinica.local ",
                "RECEPCIONISTA",
                "44999999999",
                "Atendente1"
        );
        when(usuarioRepository.findByEmail("recepcao.nova@clinica.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Atendente1")).thenReturn("$2a$12$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        EquipeUsuarioResponse response = service.criarUsuario(clinica, request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario usuario = usuarioCaptor.getValue();
        assertInstanceOf(Recepcionista.class, usuario);
        assertEquals(clinica, usuario.getClinica());
        assertEquals("Recepcao Nova", usuario.getNome());
        assertEquals("recepcao.nova@clinica.local", usuario.getEmail());
        assertEquals("44999999999", usuario.getTelefone());
        assertEquals("$2a$12$hash", usuario.getSenhaHash());
        assertEquals("CLARO", usuario.getTemaPreferencia());
        assertTrue(usuario.getAtivo());
        assertTrue(usuario.getMustChangePassword());
        assertFalse(usuario.getAdminInterno());
        assertEquals("RECEPCIONISTA", response.perfil());
        verify(clinicDataChangePublisher).publish(7L);
    }

    @Test
    void should_create_doctor_with_first_login_required() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Medico Novo",
                "medico.novo@clinica.local",
                "MEDICO",
                null,
                "Medico1"
        );
        when(usuarioRepository.findByEmail("medico.novo@clinica.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Medico1")).thenReturn("$2a$12$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        EquipeUsuarioResponse response = service.criarUsuario(clinica, request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertInstanceOf(Medico.class, usuarioCaptor.getValue());
        assertTrue(usuarioCaptor.getValue().getMustChangePassword());
        assertEquals("MEDICO", response.perfil());
    }

    @Test
    void should_reject_weak_temporary_password() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Usuario Fraco",
                "fraco@clinica.local",
                "GESTOR",
                null,
                "123456"
        );

        assertThrows(BadRequestException.class, () -> service.criarUsuario(clinica, request));
        verify(usuarioRepository, never()).save(any());
        verify(clinicDataChangePublisher, never()).publish(any());
    }

    @Test
    void should_preserve_temporary_password_with_special_characters() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Usuario Especial",
                "especial@clinica.local",
                "GESTOR",
                null,
                " Senha@123 "
        );

        when(usuarioRepository.findByEmail("especial@clinica.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(" Senha@123 ")).thenReturn("$2a$12$special");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.criarUsuario(clinica, request);

        verify(passwordEncoder).encode(" Senha@123 ");
        verify(usuarioRepository).save(any(Gestor.class));
    }

    @Test
    void should_reject_duplicate_email() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Usuario Duplicado",
                "duplicado@clinica.local",
                "GESTOR",
                null,
                "Gestor2026"
        );
        when(usuarioRepository.findByEmail("duplicado@clinica.local"))
                .thenReturn(Optional.of(usuario(new Gestor(), 1L, "Existente", "duplicado@clinica.local", "GESTOR")));

        assertThrows(BadRequestException.class, () -> service.criarUsuario(clinica, request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void should_reject_invalid_profile() {
        EquipeUsuarioCreateRequest request = new EquipeUsuarioCreateRequest(
                "Usuario Perfil",
                "perfil@clinica.local",
                "ADMIN",
                null,
                "Gestor2026"
        );

        assertThrows(BadRequestException.class, () -> service.criarUsuario(clinica, request));
        verify(usuarioRepository, never()).save(any());
    }

    private List<String> nomes(EquipeGrupoResponse grupo) {
        return grupo.usuarios().stream().map(EquipeUsuarioResponse::nome).toList();
    }

    private <T extends Usuario> T usuario(T usuario, Long id, String nome, String email, String perfil) {
        usuario.setId(id);
        usuario.setClinica(clinica);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setMustChangePassword(false);
        usuario.setAdminInterno(false);
        return usuario;
    }
}
