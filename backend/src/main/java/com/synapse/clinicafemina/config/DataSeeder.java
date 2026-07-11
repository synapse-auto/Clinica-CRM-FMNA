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

    @Value("${app.initial-users.allow-password-reset:false}")
    private boolean allowPasswordReset;

    @Value("${app.environment:teste}")
    private String applicationEnvironment;

    @Value("${app.clinic.slug:fmna}")
    private String clinicSlug;

    @Override
    @Transactional
    public void run(String... args) {
        boolean hasPassword = false;
        boolean hasSenha = false;
        for (java.lang.reflect.RecordComponent rc : InitialUserDefinition.class.getRecordComponents()) {
            if ("password".equals(rc.getName())) hasPassword = true;
            if ("senha".equals(rc.getName())) hasSenha = true;
        }
        String campoSenhaEsperado = hasPassword ? "password" : (hasSenha ? "senha" : "desconhecido");

        log.info("Diagnóstico DataSeeder: enabled={}", initialUsersEnabled);
        log.info("Diagnóstico DataSeeder: campo de senha esperado = '{}'", campoSenhaEsperado);

        if (!initialUsersEnabled) {
            if (initialUsersJson != null && !initialUsersJson.isBlank() && !initialUsersJson.equals("[]")) {
                try {
                    List<InitialUserDefinition> tempDefs = objectMapper.readValue(
                            initialUsersJson,
                            new TypeReference<List<InitialUserDefinition>>() {}
                    );
                    log.info("Diagnóstico DataSeeder: JSON lido com sucesso (seeder desabilitado). Quantidade = {}", tempDefs.size());
                } catch (Exception e) {
                    log.error("Diagnóstico DataSeeder: JSON possui formato inválido (seeder desabilitado). Motivo: {}", e.getMessage());
                }
            } else {
                log.info("0 definições de usuários encontradas");
            }
            log.info("Seed de usuários iniciais desabilitado (INITIAL_USERS_ENABLED=false).");
            return;
        }

        try {
            Clinica clinica = clinicaRepository.findBySlug(clinicSlug)
                    .orElseThrow(() -> new IllegalStateException(
                            "Clínica com slug '" + clinicSlug + "' não encontrada no banco de dados para o seeder"
                    ));

            List<InitialUserDefinition> definitions = null;
            boolean jsonLidoComSucesso = false;
            try {
                definitions = parseDefinitions();
                jsonLidoComSucesso = true;
                int totalDefs = definitions.size();
                if (totalDefs == 1) {
                    log.info("1 definição de usuário encontrada");
                } else {
                    log.info("{} definições de usuários encontradas", totalDefs);
                }
            } catch (Exception e) {
                log.error("Diagnóstico DataSeeder: erro ao ler INITIAL_USERS_JSON. Motivo: {}", e.getMessage());
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("Falha crítica ao ler definições de usuários iniciais.", e);
            }

            int created = 0;
            int reset = 0;
            int skipped = 0;
            int blockedResets = 0;

            for (InitialUserDefinition definition : definitions) {
                String maskedEmail = maskEmail(definition.email());
                log.info("processando usuário {}", maskedEmail);
                try {
                    validate(definition);
                    String normalizedEmail = definition.email().trim().toLowerCase(Locale.ROOT);
                    Usuario existing = usuarioRepository.findByEmail(normalizedEmail).orElse(null);

                    if (existing != null) {
                        if (Boolean.TRUE.equals(definition.resetPassword())) {
                            if (canResetExistingPassword()) {
                                log.warn("Redefinição de senha autorizada por opt-in explícito para {}.", maskedEmail);
                                resetPassword(existing, definition);
                                log.info("Senha inicial redefinida; troca obrigatória habilitada para {}.", maskedEmail);
                                reset++;
                            } else {
                                log.warn("Redefinição de senha bloqueada pelo ambiente para {}.", maskedEmail);
                                blockedResets++;
                            }
                        } else {
                            log.info("Usuário existente encontrado para {}. Ignorando redefinição de senha (resetPassword=false).", maskedEmail);
                            skipped++;
                        }
                        continue;
                    }

                    log.info("Usuário {} não encontrado no banco. Criando novo usuário.", maskedEmail);
                    usuarioRepository.save(createUser(clinica, definition, normalizedEmail));
                    log.info("usuário criado");
                    log.info("mustChangePassword=true");
                    created++;
                } catch (Exception e) {
                    log.error("Erro ao processar usuário {} no seeder. Motivo: {}", maskedEmail, e.getMessage());
                    throw e; // Relança para expor falha
                }
            }

            log.info(
                    "Seed de usuários iniciais concluído com sucesso. criados={}, redefinidos={}, ignorados={}, redefiniçõesBloqueadas={}",
                    created,
                    reset,
                    skipped,
                    blockedResets
            );
        } catch (Exception e) {
            log.error("Falha geral na execução do DataSeeder. Motivo: {}", e.getMessage());
            throw e;
        }
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

    private boolean canResetExistingPassword() {
        return allowPasswordReset && isDevelopmentOrTestEnvironment();
    }

    private boolean isDevelopmentOrTestEnvironment() {
        String normalized = applicationEnvironment == null
                ? ""
                : applicationEnvironment.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("dev")
                || normalized.equals("development")
                || normalized.equals("desenvolvimento")
                || normalized.equals("test")
                || normalized.equals("teste")
                || normalized.equals("local");
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

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "vazio";
        }
        String trimmed = email.trim();
        int atIdx = trimmed.indexOf('@');
        if (atIdx <= 0) {
            return "***";
        }
        String local = trimmed.substring(0, atIdx);
        String domain = trimmed.substring(atIdx);
        if (local.length() <= 3) {
            return "***" + domain;
        }
        return local.substring(0, 2) + "***" + local.substring(local.length() - 1) + domain;
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
