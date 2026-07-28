package com.synapse.clinicafemina.security;

import com.synapse.clinicafemina.domain.Gestor;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JwtService — janela de sessão de 48 horas (FMNA)")
class JwtServiceTest {

    private JwtService servicoCom(long expirationMs) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey",
                "test-only-secret-key-with-at-least-32-bytes");
        ReflectionTestUtils.setField(service, "jwtExpiration", expirationMs);
        return service;
    }

    private Gestor usuarioTeste() {
        Gestor usuario = new Gestor();
        usuario.setEmail("gestor@clinica.local");
        return usuario;
    }

    @Test
    void should_limit_token_expiration_to_48_hours_when_configuration_is_longer() {
        JwtService service = servicoCom(Duration.ofHours(72).toMillis());

        String token = service.generateToken(usuarioTeste());
        Date issuedAt = service.extractClaim(token, Claims::getIssuedAt);
        Date expiration = service.extractClaim(token, Claims::getExpiration);

        assertTrue(expiration.getTime() - issuedAt.getTime() <= Duration.ofHours(48).toMillis());
    }

    @Test
    @DisplayName("JWT_EXPIRATION_MS=172800000 (48h) produz exp-iat de aproximadamente 172800 segundos")
    void should_set_expiration_to_approximately_172800_seconds_for_fmna_configuration() {
        JwtService service = servicoCom(172_800_000L); // JWT_EXPIRATION_MS esperado na FMNA

        String token = service.generateToken(usuarioTeste());
        Date issuedAt = service.extractClaim(token, Claims::getIssuedAt);
        Date expiration = service.extractClaim(token, Claims::getExpiration);

        long diferencaSegundos = (expiration.getTime() - issuedAt.getTime()) / 1000;
        assertTrue(diferencaSegundos >= 172_799 && diferencaSegundos <= 172_800,
                "esperado ~172800s, obtido " + diferencaSegundos);
    }

    @Test
    @DisplayName("token continua válido antes das 48 horas")
    void should_consider_token_valid_before_expiration() {
        JwtService service = servicoCom(172_800_000L);
        Gestor usuario = usuarioTeste();

        String token = service.generateToken(usuario);

        assertTrue(service.isTokenValid(token, usuario));
    }

    @Test
    @DisplayName("token expirado é rejeitado (ExpiredJwtException) — capturado e tratado como 401 pelo JwtAuthenticationFilter")
    void should_reject_token_after_expiration() throws InterruptedException {
        JwtService service = servicoCom(1L); // expira em 1ms
        Gestor usuario = usuarioTeste();

        String token = service.generateToken(usuario);
        Thread.sleep(20);

        // isTokenValid propaga ExpiredJwtException (contrato real da lib JJWT); o filtro de
        // autenticação (JwtAuthenticationFilter) já captura essa exceção e nega o acesso (401).
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> service.isTokenValid(token, usuario));
    }
}
