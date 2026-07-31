package com.synapse.clinicafemina.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Protege somente as rotas do springdoc com credenciais independentes do CRM. */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class ApiDocsSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiDocsSecurityFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Value("${API_DOCS_USERNAME:}") String username,
            @Value("${API_DOCS_PASSWORD:}") String password
    ) throws Exception {
        validarCredenciais(username, password);
        UserDetailsService users = new InMemoryUserDetailsManager(User.withUsername(username.trim())
                .password(passwordEncoder.encode(password))
                .roles("API_DOCS")
                .build());
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(passwordEncoder);

        http
                .securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    static void validarCredenciais(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("API_DOCS_USERNAME é obrigatória quando API_DOCS_ENABLED=true.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("API_DOCS_PASSWORD é obrigatória quando API_DOCS_ENABLED=true.");
        }
        if (password.length() < 12 || password.equalsIgnoreCase(username)) {
            throw new IllegalStateException("API_DOCS_PASSWORD não atende aos requisitos mínimos de segurança.");
        }
    }
}
