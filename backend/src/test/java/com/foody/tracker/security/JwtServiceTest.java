package com.foody.tracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = "foody-tracker-unit-test-secret-key-32b+";
    private static final String OTHER_SECRET = "foody-tracker-another-secret-key-32b++";

    private final User user = user();

    @Test
    void generatedTokenCarriesSubjectAndCustomClaims() {
        JwtService jwtService = jwtService(SECRET, 7200);

        String token = jwtService.generateToken(user);
        Claims claims = jwtService.parse(token);

        assertThat(new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8))
                .contains("HS256");
        assertThat(claims.getSubject()).isEqualTo("ana@example.com");
        assertThat(claims.get("uid", Long.class)).isEqualTo(7L);
        assertThat(claims.get("role", String.class)).isEqualTo("CLIENT");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration().toInstant())
                .isEqualTo(claims.getIssuedAt().toInstant().plus(7200, ChronoUnit.SECONDS));
    }

    @Test
    void expirationSecondsIsExposed() {
        assertThat(jwtService(SECRET, 7200).getExpirationSeconds()).isEqualTo(7200);
    }

    @Test
    void parseRejectsExpiredToken() {
        JwtService jwtService = jwtService(SECRET, -60);
        String token = jwtService.generateToken(user);

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsTokenSignedWithAnotherKey() {
        String token = jwtService(OTHER_SECRET, 7200).generateToken(user);
        JwtService jwtService = jwtService(SECRET, 7200);

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsTamperedToken() {
        JwtService jwtService = jwtService(SECRET, 7200);
        String[] parts = jwtService.generateToken(user).split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'e' ? 'f' : 'e';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    private JwtService jwtService(String secret, long expirationSeconds) {
        return new JwtService(new JwtProperties(secret, expirationSeconds));
    }

    private User user() {
        User user = new User("Ana Souza", "ana@example.com", "hashed", Role.CLIENT);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }
}
