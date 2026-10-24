package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henheang.commonapi.components.common.api.StatusCode;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.BusinessException;
import com.henheang.securityapi.payload.SupabaseTokenResponse;
import com.henheang.securityapi.security.supabase.SupabaseAdminClient;
import com.henheang.securityapi.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupabaseBridgeServiceImplTest {

    // Fixed, non-sensitive test value in the shape of a Supabase legacy JWT secret.
    private static final String JWT_SECRET = "test-only-supabase-legacy-jwt-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    private UserService userService;
    private SupabaseAdminClient adminClient;
    private SupabaseBridgeServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        adminClient = mock(SupabaseAdminClient.class);
        service = bridge("https://project.supabase.co", JWT_SECRET, "service-role-key");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        when(userService.getUserById(user.getId())).thenReturn(user);
    }

    private SupabaseBridgeServiceImpl bridge(String url, String secret, String serviceRoleKey) {
        return new SupabaseBridgeServiceImpl(
                userService,
                adminClient,
                url,
                secret,
                serviceRoleKey,
                Duration.ofHours(1),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Claims parse(String token) {
        // Verified with the clock's "now" so the fixed-time token isn't seen as expired.
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .setClock(() -> java.util.Date.from(NOW))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Test
    void issueToken_whenNotConfigured_failsWith503() {
        SupabaseBridgeServiceImpl disabled = bridge("https://project.supabase.co", "", "key");

        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.issueToken(user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StatusCode.SERVICE_UNAVAILABLE);
        verify(adminClient, never()).userExists(any());
    }

    @Test
    void issueToken_signsClaimsThatSupabaseRlsExpects() {
        when(adminClient.userExists(user.getId())).thenReturn(true);

        SupabaseTokenResponse response = service.issueToken(user.getId());
        Claims claims = parse(response.getAccessToken());

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role")).isEqualTo("authenticated");
        assertThat(claims.getAudience()).isEqualTo("authenticated");
        assertThat(claims.get("email")).isEqualTo("jane@example.com");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
    }

    @Test
    void issueToken_createsMissingSupabaseUserOnceWithTheSameId() {
        when(adminClient.userExists(user.getId())).thenReturn(false);

        service.issueToken(user.getId());
        service.issueToken(user.getId());

        verify(adminClient, times(1)).userExists(user.getId());
        verify(adminClient, times(1)).createUser(user.getId(), "jane@example.com");
    }

    @Test
    void issueToken_doesNotRecreateAnExistingSupabaseUser() {
        when(adminClient.userExists(user.getId())).thenReturn(true);

        service.issueToken(user.getId());

        verify(adminClient, never()).createUser(any(), any());
    }

    @Test
    void issueToken_propagatesAPreMigrationEmailConflict() {
        when(adminClient.userExists(user.getId())).thenReturn(false);
        BusinessException conflict = new BusinessException(StatusCode.CONFLICT, "needs import");
        org.mockito.Mockito.doThrow(conflict)
                .when(adminClient)
                .createUser(user.getId(), "jane@example.com");

        assertThatThrownBy(() -> service.issueToken(user.getId())).isSameAs(conflict);

        // Not cached as provisioned, so a later attempt (after the import) re-checks.
        assertThatThrownBy(() -> service.issueToken(user.getId())).isSameAs(conflict);
        verify(adminClient, times(2)).userExists(user.getId());
    }

    @Test
    void issueToken_withoutEmail_cannotProvision() {
        user.setEmail(null);
        when(adminClient.userExists(user.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.issueToken(user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(StatusCode.CONFLICT);
        verify(adminClient, never()).createUser(any(), any());
    }
}
