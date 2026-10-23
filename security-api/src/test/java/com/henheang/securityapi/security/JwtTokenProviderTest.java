package com.henheang.securityapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS512);
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecretKey", signingKey);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationString", "PT24H");
    }

    private User userWithId(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void generateToken_roundTripsUserIdAndJti() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateToken(userWithId(userId));

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getJtiFromToken(token)).isNotBlank();
        assertThat(jwtTokenProvider.getExpirationFromToken(token)).isAfter(java.time.Instant.now());
    }

    @Test
    void generateToken_withTokenType_isRecoverable() {
        String token =
                jwtTokenProvider.generateToken(
                        UUID.randomUUID(), "mfa_challenge", Duration.ofMinutes(5));

        assertThat(jwtTokenProvider.getTokenTypeFromToken(token)).isEqualTo("mfa_challenge");
    }

    @Test
    void generateToken_withoutTokenType_hasNullTokenType() {
        String token = jwtTokenProvider.generateToken(userWithId(UUID.randomUUID()));

        assertThat(jwtTokenProvider.getTokenTypeFromToken(token)).isNull();
    }

    @Test
    void validateToken_returnsFalseForGarbage() {
        assertThat(jwtTokenProvider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        String token =
                jwtTokenProvider.generateToken(UUID.randomUUID(), null, Duration.ofMillis(1));
        Thread.sleep(10);

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalseWhenSignedWithDifferentKey() {
        JwtTokenProvider otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(
                otherProvider,
                "jwtSecretKey",
                Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS512));
        ReflectionTestUtils.setField(otherProvider, "jwtExpirationString", "PT24H");
        String tokenSignedByOther = otherProvider.generateToken(userWithId(UUID.randomUUID()));

        assertThat(jwtTokenProvider.validateToken(tokenSignedByOther)).isFalse();
    }

    @Test
    void getUserIdFromToken_onInvalidToken_throwsAuthException() {
        assertThatThrownBy(() -> jwtTokenProvider.getUserIdFromToken("garbage"))
                .isInstanceOf(AuthException.class);
    }
}
