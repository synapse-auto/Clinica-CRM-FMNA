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
import com.synapse.clinicafemina.dto.equipe.PermissaoGerenciamentoRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.security.PasswordPolicy;
import com.synapse.clinicafemina.service.audit.UsuarioPermissionAuditEvent;
import com.synapse.clinicafemina.service.cache.ClinicDataChangePublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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
    private final ClinicaRepository clinicaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClinicDataChangePublisher clinicDataChangePublisher;
    private final UsuarioPermissionService usuarioPermissionService;
    private final ApplicationEventPublisher applicationEventPublisher;

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
        usuario.setPodeGerenciarUsuarios(false);
        usuario.setTemaPreferencia("CLARO");

        EquipeUsuarioResponse response = toResponse(usuarioRepository.save(usuario), perfil);
        clinicDataChangePublisher.publish(clinica.getId());
        return response;
    }

    @Transactional
    public EquipeUsuarioResponse alterarPermissaoGerenciamento(
            Long usuarioId,
            PermissaoGerenciamentoRequest request,
            Authentication authentication
    ) {
        Usuario executor = usuarioPermissionService.exigirGerenciador(authentication);
        Long clinicaId = executor.getClinica().getId();

        clinicaRepository.findByIdForUpdate(clinicaId)
                .orElseThrow(() -> new AccessDeniedException("Acesso negado."));
        Usuario alvo = usuarioRepository.findByIdAndClinicaIdForUpdate(usuarioId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        validarAlvoDaPermissao(alvo);

        boolean valorAnterior = Boolean.TRUE.equals(alvo.getPodeGerenciarUsuarios());
        boolean valorNovo = Boolean.TRUE.equals(request.podeGerenciarUsuarios());
        if (valorAnterior == valorNovo) {
            return toResponse(alvo);
        }

        if (!valorNovo && usuarioRepository.countGestoresAutorizadosAtivosByClinicaId(clinicaId) <= 1) {
            throw new BadRequestException("A clínica precisa manter ao menos um gestor com essa permissão.");
        }

        alvo.setPodeGerenciarUsuarios(valorNovo);
        EquipeUsuarioResponse response = toResponse(usuarioRepository.save(alvo));
        applicationEventPublisher.publishEvent(new UsuarioPermissionAuditEvent(
                "PERMISSAO_GERENCIAMENTO_USUARIOS_ALTERADA",
                executor.getId(),
                alvo.getId(),
                clinicaId,
                valorAnterior,
                valorNovo,
                OffsetDateTime.now()
        ));
        return response;
    }

    private void validarAlvoDaPermissao(Usuario alvo) {
        if (Boolean.TRUE.equals(alvo.getAdminInterno())) {
            throw new AccessDeniedException("Usuário interno não pode receber esta permissão.");
        }
        if (!Boolean.TRUE.equals(alvo.getAtivo()) || alvo.getDeletadoEm() != null) {
            throw new BadRequestException("A permissão só pode ser alterada para um usuário ativo e não deletado.");
        }
        if (!"GESTOR".equals(resolvePerfil(alvo).perfil())) {
            throw new BadRequestException("A permissão só pode ser concedida a gestores.");
        }
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
                Boolean.TRUE.equals(usuario.getPodeGerenciarUsuarios()),
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
