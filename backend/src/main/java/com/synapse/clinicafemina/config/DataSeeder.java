package com.synapse.clinicafemina.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.security.PasswordPolicy;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Pattern PLAIN_EMAIL = Pattern.compile(
            "^[^\\s@\\[\\]()]+@[^\\s@\\[\\]()]+\\.[^\\s@\\[\\]()]+$"
    );

    private final ClinicaRepository clinicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${app.initial-users.enabled:false}")
    private boolean initialUsersEnabled;

    @Value("${app.initial-users.json:[]}")
    private String initialUsersJson;

    @Value("${app.clinic.slug:fmna}")
    private String clinicSlug;

    @Override
    @Transactional
    public void run(String... args) {
        if (!initialUsersEnabled) {
            log.debug("Seed de usuários iniciais desabilitado.");
            return;
        }

        Clinica clinica = clinicaRepository.findBySlug(clinicSlug)
                .orElseThrow(() -> new IllegalStateException(
                        "Clínica configurada não encontrada para o seed de usuários iniciais"
                ));
        List<InitialUserDefinition> definitions = parseDefinitions();
        int created = 0;
        int reset = 0;
        int skipped = 0;

        for (InitialUserDefinition definition : definitions) {
            validate(definition);
            String normalizedEmail = definition.email().trim().toLowerCase(Locale.ROOT);
            Usuario existing = usuarioRepository.findByEmail(normalizedEmail).orElse(null);
            if (existing != null) {
                if (Boolean.TRUE.equals(definition.resetPassword())) {
                    resetPassword(existing, definition);
                    reset++;
                } else {
                    skipped++;
                }
                continue;
            }

            usuarioRepository.save(createUser(clinica, definition, normalizedEmail));
            created++;
        }

        log.info(
                "Seed de usuários iniciais concluído. criados={}, redefinidos={}, ignorados={}",
                created,
                reset,
                skipped
        );
    }

    private List<InitialUserDefinition> parseDefinitions() {
        try {
            return objectMapper.readValue(
                    initialUsersJson,
                    new TypeReference<List<InitialUserDefinition>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "INITIAL_USERS_JSON possui formato inválido. Use JSON puro, sem Markdown ou mailto."
            );
        }
    }

    private Usuario createUser(
            Clinica clinica,
            InitialUserDefinition definition,
            String normalizedEmail
    ) {
        Usuario usuario = createProfile(definition.perfil());
        usuario.setClinica(clinica);
        usuario.setNome(definition.nome().trim());
        usuario.setEmail(normalizedEmail);
        usuario.setSenhaHash(passwordEncoder.encode(definition.password()));
        usuario.setMustChangePassword(true);
        usuario.setAdminInterno(Boolean.TRUE.equals(definition.adminInterno()));
        usuario.setAtivo(true);
        usuario.setTemaPreferencia("CLARO");
        return usuario;
    }

    private void resetPassword(Usuario existing, InitialUserDefinition definition) {
        String existingProfile = existing.getPerfil();
        if (existingProfile != null && !existingProfile.equals(definition.perfil().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("Perfil do usuário existente diverge do INITIAL_USERS_JSON");
        }
        existing.setSenhaHash(passwordEncoder.encode(definition.password()));
        existing.setMustChangePassword(true);
        usuarioRepository.save(existing);
    }

    private Usuario createProfile(String profile) {
        return switch (profile.trim().toUpperCase(Locale.ROOT)) {
            case "GESTOR" -> new Gestor();
            case "RECEPCIONISTA" -> new Recepcionista();
            case "MEDICO" -> new Medico();
            default -> throw new IllegalStateException("Perfil inválido em INITIAL_USERS_JSON");
        };
    }

    private void validate(InitialUserDefinition definition) {
        if (isBlank(definition.nome()) || isBlank(definition.email()) || isBlank(definition.perfil())) {
            throw new IllegalStateException("Usuário inicial possui campos obrigatórios ausentes");
        }
        validateEmail(definition.email());
        validatePassword(definition.password());
        if (Boolean.TRUE.equals(definition.adminInterno())
                && !"GESTOR".equalsIgnoreCase(definition.perfil().trim())) {
            throw new IllegalStateException("Admin interno deve utilizar o perfil GESTOR");
        }
    }

    private void validatePassword(String password) {
        if (!PasswordPolicy.isStrong(password)) {
            throw new IllegalStateException("Senha inicial não atende aos requisitos mínimos de segurança");
        }
    }

    private void validateEmail(String email) {
        String normalizedEmail = email.trim();
        if (normalizedEmail.toLowerCase(Locale.ROOT).contains("mailto:")
                || !PLAIN_EMAIL.matcher(normalizedEmail).matches()) {
            throw new IllegalStateException("Email inválido em INITIAL_USERS_JSON");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record InitialUserDefinition(
            String nome,
            String email,
            String perfil,
            String password,
            Boolean mustChangePassword,
            Boolean adminInterno,
            Boolean resetPassword
    ) {}
}
