package com.henheang.hubapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * JWT-11 for the pilot's configuration: it must refuse to start rather than run with a missing or
 * insecure project URL, or with a secret key where the publishable one belongs. Values synthetic.
 */
class HubSupabaseAuthPropertiesTest {

    private static final String KEY = "sb_publishable_synthetic_test_only";

    private static HubSupabaseAuthProperties props(String url, String key) {
        return new HubSupabaseAuthProperties(
                true, url, key, "authenticated", Duration.ofSeconds(3));
    }

    @Test
    void derivesTheIssuer_fromTheProjectUrl() {
        HubSupabaseAuthProperties ok = props("https://example-ref.supabase.co/", KEY).validated();
        assertThat(ok.issuer()).isEqualTo("https://example-ref.supabase.co/auth/v1");
    }

    @Test
    void loopbackHttp_isAllowed_forALocalStack() {
        assertThat(props("http://127.0.0.1:54321", KEY).validated().issuer())
                .isEqualTo("http://127.0.0.1:54321/auth/v1");
    }

    @Test
    void missingOrInsecureUrl_failsFast() {
        for (String url :
                new String[] {null, " ", "http://example-ref.supabase.co", "example.supabase.co"}) {
            assertThatThrownBy(() -> props(url, KEY).validated())
                    .as(String.valueOf(url))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void missingKey_failsFast() {
        assertThatThrownBy(() -> props("https://example-ref.supabase.co", " ").validated())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretOrServiceRoleKey_isRefused() {
        String serviceRoleShaped =
                "eyJhbGciOiJIUzI1NiJ9."
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        "{\"role\":\"service_role\"}"
                                                .getBytes(StandardCharsets.UTF_8))
                        + ".c3ludGhldGlj";
        for (String key : new String[] {"sb_secret_synthetic", serviceRoleShaped}) {
            assertThatThrownBy(() -> props("https://example-ref.supabase.co", key).validated())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(key);
        }
    }

    @Test
    void anonShapedLegacyKey_isAccepted() {
        String anonShaped =
                "eyJhbGciOiJIUzI1NiJ9."
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        "{\"role\":\"anon\"}".getBytes(StandardCharsets.UTF_8))
                        + ".c3ludGhldGlj";
        assertThat(props("https://example-ref.supabase.co", anonShaped).validated()).isNotNull();
    }

    @Test
    void timeoutOutsideBounds_failsFast() {
        assertThatThrownBy(
                        () ->
                                new HubSupabaseAuthProperties(
                                                true,
                                                "https://example-ref.supabase.co",
                                                KEY,
                                                "authenticated",
                                                Duration.ofMinutes(5))
                                        .validated())
                .isInstanceOf(IllegalStateException.class);
    }
}
