package com.henheang.hubapi.config;

import com.henheang.supabaseauth.SupabaseJwtDecoders;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Auth V2 pilot (docs/auth-v2.md): Supabase sessions on {@code /api/hub/session} and {@code
 * /api/hub/inbox-items}. Off by default; while off, nothing here is read and the Inbox keeps
 * security-api's own JWTs exactly as before.
 *
 * <p>Only <em>public</em> project configuration: the project URL and its publishable (anon) key.
 * Never the legacy JWT secret, the service-role key or any signing key - {@link #validated()}
 * refuses a key that looks like one.
 *
 * @param enabled turns the pilot chain on ({@code HUB_SUPABASE_AUTH_ENABLED})
 * @param url the Supabase project URL, e.g. {@code https://<ref>.supabase.co}; the token issuer is
 *     {@code <url>/auth/v1}, its keys {@code <url>/auth/v1/.well-known/jwks.json}, and admission is
 *     read from {@code <url>/rest/v1/kori_app_access} ({@code HUB_SUPABASE_URL})
 * @param publishableKey the project's publishable key, sent as {@code apikey} with the caller's own
 *     bearer token to read the caller's own admission row ({@code HUB_SUPABASE_PUBLISHABLE_KEY})
 * @param audience the {@code aud} a token must carry; Supabase uses {@code authenticated}
 * @param admissionTimeout how long one admission lookup may take before the request gets 503
 */
@ConfigurationProperties("hub.supabase-auth")
public record HubSupabaseAuthProperties(
        @DefaultValue("false") boolean enabled,
        String url,
        String publishableKey,
        @DefaultValue("authenticated") String audience,
        @DefaultValue("3s") Duration admissionTimeout) {

    /** The project URL without a trailing slash. */
    public String normalizedUrl() {
        String trimmed = url == null ? "" : url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** The token issuer Supabase Auth writes in {@code iss}. */
    public String issuer() {
        return normalizedUrl() + "/auth/v1";
    }

    /**
     * Fails startup instead of running the pilot misconfigured: no URL, a non-https remote URL, no
     * key, a key that is evidently secret, or a blank audience / non-positive timeout.
     */
    public HubSupabaseAuthProperties validated() {
        if (normalizedUrl().isEmpty()) {
            throw new IllegalStateException(
                    "hub.supabase-auth.url (HUB_SUPABASE_URL) is required when the pilot is on");
        }
        try {
            // Same https-only (http only on loopback) rule as the token issuer.
            SupabaseJwtDecoders.normalizeIssuer(issuer());
        } catch (IllegalArgumentException insecure) {
            throw new IllegalStateException(
                    "hub.supabase-auth.url (HUB_SUPABASE_URL): " + insecure.getMessage());
        }
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new IllegalStateException(
                    "hub.supabase-auth.publishable-key (HUB_SUPABASE_PUBLISHABLE_KEY) is required"
                            + " when the pilot is on");
        }
        if (looksSecret(publishableKey.trim())) {
            throw new IllegalStateException(
                    "hub.supabase-auth.publishable-key must be the project's publishable (anon)"
                            + " key, never a secret or service-role key");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException("hub.supabase-auth.audience must not be blank");
        }
        if (admissionTimeout == null
                || admissionTimeout.isNegative()
                || admissionTimeout.isZero()
                || admissionTimeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalStateException(
                    "hub.supabase-auth.admission-timeout must be between 1 ms and 10 s");
        }
        return this;
    }

    /**
     * A new-style secret key ({@code sb_secret_...}) or a legacy JWT-shaped key whose {@code role}
     * is {@code service_role}. Never decoded for anything else, and never logged.
     */
    static boolean looksSecret(String key) {
        if (key.toLowerCase(Locale.ROOT).startsWith("sb_secret_")) {
            return true;
        }
        String[] parts = key.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return payload.replace(" ", "").contains("\"role\":\"service_role\"");
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }
}
