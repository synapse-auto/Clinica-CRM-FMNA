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
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.security.PasswordPolicy;
import com.synapse.clinicafemina.service.cache.ClinicDataChangePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminUsuarioService {

    private static final Pattern PLAIN_EMAIL = Pattern.compile(
            "^[^\\s@\\[\\]()]+@[^\\s@\\[\\]()]+\\.[^\\s@\\[\\]()]+$"
    );

    private final UsuarioRepository usuarioRepository;
    private final ClinicaRepository clinicaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClinicDataChangePublisher clinicDataChangePublisher;

    @Transactional(readOnly = true)
    public List<EquipeUsuarioResponse> listar(Clinica clinica) {
        List<Usuario> usuarios = usuarioRepository.findTodosByClinicaId(clinica.getId());
        return usuarios.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EquipeUsuarioResponse criarUsuario(Clinica clinica, EquipeUsuarioCreateRequest request) {
        String perfil = normalizeProfile(request.perfil());
        String email = normalizeEmail(request.email());
        String nome = normalizeRequired(request.nome(), "Nome é obrigatório.");
        String senhaTemporaria = requirePassword(request.senhaTemporaria());

        if (!PasswordPolicy.isStrong(senhaTemporaria)) {
            throw new BadRequestException(PasswordPolicy.MESSAGE);
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
        usuario.setPodeGerenciarUsuarios(false);

        EquipeUsuarioResponse response = toResponse(usuarioRepository.save(usuario));
        clinicDataChangePublisher.publish(clinica.getId());
        return response;
    }

    @Transactional
    public EquipeUsuarioResponse alterarStatus(Clinica clinica, Long id, StatusRequest request) {
        clinicaRepository.findByIdForUpdate(clinica.getId())
                .orElseThrow(() -> new NotFoundException("Clínica não encontrada."));
        Usuario usuario = usuarioRepository.findByIdAndClinicaIdForUpdate(id, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        // Impedir auto-desativação
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Usuario currentAuthUser && currentAuthUser.getId().equals(usuario.getId()) && !request.ativo()) {
            throw new BadRequestException("Não é permitido desativar a si mesmo.");
        }

        if (!request.ativo()
                && Boolean.TRUE.equals(usuario.getAtivo())
                && isGestorAutorizadoAtivo(usuario)
                && usuarioRepository.countGestoresAutorizadosAtivosByClinicaId(clinica.getId()) <= 1) {
            throw new BadRequestException("A clínica precisa manter ao menos um gestor com essa permissão.");
        }

        usuario.setAtivo(request.ativo());
        EquipeUsuarioResponse response = toResponse(usuarioRepository.save(usuario));
        clinicDataChangePublisher.publish(clinica.getId());
        return response;
    }

    @Transactional
    public EquipeUsuarioResponse resetarSenha(Clinica clinica, Long id, ResetPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByIdAndClinicaId(id, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        String senhaTemporaria = request.senhaTemporaria();
        if (senhaTemporaria == null || senhaTemporaria.isBlank()) {
            throw new BadRequestException("A senha temporária é obrigatória.");
        }

        if (!PasswordPolicy.isStrong(senhaTemporaria)) {
            throw new BadRequestException(PasswordPolicy.MESSAGE);
        }

        usuario.setSenhaHash(passwordEncoder.encode(senhaTemporaria));
        usuario.setMustChangePassword(true);
        return toResponse(usuarioRepository.save(usuario));
    }

    private boolean isGestorAutorizadoAtivo(Usuario usuario) {
        return "GESTOR".equals(usuario.getPerfil())
                && Boolean.TRUE.equals(usuario.getAtivo())
                && usuario.getDeletadoEm() == null
                && !Boolean.TRUE.equals(usuario.getAdminInterno())
                && Boolean.TRUE.equals(usuario.getPodeGerenciarUsuarios());
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

    private String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("A senha temporária é obrigatória.");
        }
        return password;
    }

    private EquipeUsuarioResponse toResponse(Usuario usuario) {
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
        return new EquipeUsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                perfil,
                usuario.getAtivo(),
                usuario.getMustChangePassword(),
                Boolean.TRUE.equals(usuario.getPodeGerenciarUsuarios()),
                usuario.getCriadoEm()
        );
    }
}
