package com.synapse.clinicafemina.security;

import com.synapse.clinicafemina.domain.Gestor;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void should_limit_token_expiration_to_one_hour_when_configuration_is_longer() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey",
                "test-only-secret-key-with-at-least-32-bytes");
        ReflectionTestUtils.setField(service, "jwtExpiration", Duration.ofHours(24).toMillis());

        Gestor usuario = new Gestor();
        usuario.setEmail("gestor@clinica.local");

        String token = service.generateToken(usuario);
        Date issuedAt = service.extractClaim(token, Claims::getIssuedAt);
        Date expiration = service.extractClaim(token, Claims::getExpiration);

        assertTrue(expiration.getTime() - issuedAt.getTime() <= Duration.ofHours(1).toMillis());
    }
}
