package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.EquipeGrupoResponse;
import com.synapse.clinicafemina.dto.equipe.EquipeResponse;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioCreateRequest;
import com.synapse.clinicafemina.dto.equipe.EquipeUsuarioResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.security.PasswordPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EquipeService {

    private static final Pattern PLAIN_EMAIL = Pattern.compile(
            "^[^\\s@\\[\\]()]+@[^\\s@\\[\\]()]+\\.[^\\s@\\[\\]()]+$"
    );
    private static final List<GroupDefinition> GROUPS = List.of(
            new GroupDefinition("GESTOR", "Gestores"),
            new GroupDefinition("MEDICO", "Médicos"),
            new GroupDefinition("RECEPCIONISTA", "Recepcionistas")
    );

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public EquipeResponse listar(Clinica clinica) {
        List<Usuario> usuarios = usuarioRepository.findAtivosVisiveisByClinicaId(clinica.getId());
        Map<String, List<EquipeUsuarioResponse>> porPerfil = usuarios.stream()
                .collect(Collectors.groupingBy(
                        usuario -> resolvePerfil(usuario).perfil(),
                        Collectors.mapping(this::toResponse, Collectors.toList())
                ));

        List<EquipeGrupoResponse> grupos = GROUPS.stream()
                .map(group -> new EquipeGrupoResponse(
                        group.perfil(),
                        group.titulo(),
                        porPerfil.getOrDefault(group.perfil(), List.of())
                ))
                .toList();

        return new EquipeResponse(grupos);
    }

    @Transactional
    public EquipeUsuarioResponse criarUsuario(Clinica clinica, EquipeUsuarioCreateRequest request) {
        String perfil = normalizeProfile(request.perfil());
        String email = normalizeEmail(request.email());
        String nome = normalizeRequired(request.nome(), "Nome é obrigatório.");
        String senhaTemporaria = normalizeRequired(request.senhaTemporaria(), "Senha temporária é obrigatória.");

        if (!PasswordPolicy.isStrong(senhaTemporaria)) {
            throw new BadRequestException("A senha temporária deve ter pelo menos 8 caracteres.");
        }
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("Email já cadastrado.");
        }

        Usuario usuario = createProfile(perfil);
        usuario.setClinica(clinica);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setTelefone(normalizeOptional(request.telefone()));
        usuario.setSenhaHash(passwordEncoder.encode(senhaTemporaria));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setMustChangePassword(true);
        usuario.setAdminInterno(false);
        usuario.setTemaPreferencia("CLARO");

        return toResponse(usuarioRepository.save(usuario), perfil);
    }

    private Usuario createProfile(String perfil) {
        return switch (perfil) {
            case "GESTOR" -> new Gestor();
            case "MEDICO" -> new Medico();
            case "RECEPCIONISTA" -> new Recepcionista();
            default -> throw new BadRequestException("Perfil inválido.");
        };
    }

    private String normalizeProfile(String perfil) {
        String normalized = normalizeRequired(perfil, "Perfil é obrigatório.").toUpperCase(Locale.ROOT);
        if (!normalized.equals("GESTOR")
                && !normalized.equals("MEDICO")
                && !normalized.equals("RECEPCIONISTA")) {
            throw new BadRequestException("Perfil inválido.");
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeRequired(email, "Email é obrigatório.").toLowerCase(Locale.ROOT);
        if (normalized.contains("mailto:") || !PLAIN_EMAIL.matcher(normalized).matches()) {
            throw new BadRequestException("Email inválido.");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private EquipeUsuarioResponse toResponse(Usuario usuario) {
        return toResponse(usuario, resolvePerfil(usuario).perfil());
    }

    private EquipeUsuarioResponse toResponse(Usuario usuario, String perfil) {
        return new EquipeUsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                perfil,
                usuario.getAtivo(),
                usuario.getMustChangePassword(),
                usuario.getCriadoEm()
        );
    }

    private GroupDefinition resolvePerfil(Usuario usuario) {
        String perfil = usuario.getPerfil();
        if (perfil == null || perfil.isBlank()) {
            if (usuario instanceof Gestor) {
                perfil = "GESTOR";
            } else if (usuario instanceof Medico) {
                perfil = "MEDICO";
            } else if (usuario instanceof Recepcionista) {
                perfil = "RECEPCIONISTA";
            } else {
                perfil = "";
            }
        }
        String normalized = perfil.toUpperCase(Locale.ROOT);
        return GROUPS.stream()
                .filter(group -> group.perfil().equals(normalized))
                .findFirst()
                .orElse(new GroupDefinition(normalized, normalized));
    }

    private record GroupDefinition(String perfil, String titulo) {
    }
}
