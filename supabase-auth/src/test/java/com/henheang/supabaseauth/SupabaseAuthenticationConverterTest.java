package com.henheang.supabaseauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authorities and session helpers for already-verified tokens. Ids are synthetic. */
class SupabaseAuthenticationConverterTest {

    private static final UUID USER_ID = UUID.fromString("3c9d2f4e-8a1b-4c7d-9e2f-0a1b2c3d4e5f");

    private final SupabaseAuthenticationConverter converter = new SupabaseAuthenticationConverter();

    @Test
    void aal2Session_isAUser_withMfa() {
        Jwt jwt = jwt(b -> b.claim("aal", "aal2"));

        assertThat(authorities(jwt))
                .containsExactlyInAnyOrder(
                        SupabaseAuthenticationConverter.USER, SupabaseAuthenticationConverter.MFA);
        assertThat(converter.convert(jwt).getName()).isEqualTo(USER_ID.toString());
        assertThat(SupabaseSession.hasMfa(jwt)).isTrue();
        assertThat(SupabaseSession.userId(jwt)).isEqualTo(USER_ID);
        assertThat(SupabaseSession.assuranceLevel(jwt)).contains("aal2");
    }

    @Test
    void passwordOnlySession_isAUser_withoutMfa() {
        Jwt jwt = jwt(b -> b.claim("aal", "aal1"));

        assertThat(authorities(jwt)).containsExactly(SupabaseAuthenticationConverter.USER);
        assertThat(SupabaseSession.hasMfa(jwt)).isFalse();
    }

    @Test
    void tokenWithoutAal_isAUser_withoutMfa() {
        Jwt jwt = jwt(b -> {});

        assertThat(authorities(jwt)).containsExactly(SupabaseAuthenticationConverter.USER);
        assertThat(SupabaseSession.assuranceLevel(jwt)).isEmpty();
    }

    @Test
    void subjectThatIsNotAUuid_getsNoAuthority_evenWithAal2() {
        Jwt jwt = jwt(b -> b.subject("service-account").claim("aal", "aal2"));

        assertThat(authorities(jwt)).isEmpty();
        assertThat(SupabaseSession.tryUserId(jwt)).isEmpty();
        assertThatThrownBy(() -> SupabaseSession.userId(jwt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenWithoutSubject_getsNoAuthority() {
        Jwt jwt = jwt(b -> b.claims(claims -> claims.remove("sub")));

        assertThat(authorities(jwt)).isEmpty();
        assertThat(SupabaseSession.tryUserId(jwt)).isEmpty();
    }

    // JWT-09: only the exact string aal2 is MFA; anything else is a plain user.
    @Test
    void unknownOrMalformedAal_isNeverMfa() {
        for (Object aal : java.util.List.<Object>of("AAL2", "aal3", "aal2 ", 2, true)) {
            Jwt jwt = jwt(b -> b.claim("aal", aal));
            assertThat(authorities(jwt))
                    .as(String.valueOf(aal))
                    .containsExactly(SupabaseAuthenticationConverter.USER);
        }
    }

    // JWT-10: neither role nor scope claims grant anything beyond SUPABASE_USER.
    @Test
    void scopeAndAdminClaims_grantNothingExtra() {
        Jwt jwt =
                jwt(
                        b ->
                                b.claim("role", "admin")
                                        .claim("scope", "admin openid")
                                        .claim("app_metadata", java.util.Map.of("role", "owner")));
        assertThat(authorities(jwt)).containsExactly(SupabaseAuthenticationConverter.USER);
    }

    @Test
    void roleClaim_isIgnored() {
        Jwt jwt = jwt(b -> b.claim("role", "service_role"));

        assertThat(authorities(jwt)).containsExactly(SupabaseAuthenticationConverter.USER);
    }

    private java.util.List<String> authorities(Jwt jwt) {
        AbstractAuthenticationToken authentication = converter.convert(jwt);
        assertThat(authentication.isAuthenticated()).isTrue();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static Jwt jwt(Consumer<Jwt.Builder> customize) {
        Instant now = Instant.now();
        Jwt.Builder builder =
                Jwt.withTokenValue("synthetic-token")
                        .header("alg", "ES256")
                        .subject(USER_ID.toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(600));
        customize.accept(builder);
        return builder.build();
    }
}
