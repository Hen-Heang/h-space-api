package com.henheang.engineeringapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Who may call engineering-api, and whose tokens it trusts. Validated at startup, so a missing
 * issuer or an empty owner list stops the app instead of running wide open or locked shut.
 *
 * @param issuer the Supabase Auth issuer, e.g. {@code https://<ref>.supabase.co/auth/v1}: the
 *     tokens' {@code iss} must equal it, and the signing keys are read from {@code
 *     <issuer>/.well-known/jwks.json} (the same derivation as the web app's {@code
 *     lib/mcp/config.ts})
 * @param audience the {@code aud} a token must carry; Supabase uses {@code authenticated} for
 *     signed-in users
 * @param ownerIds the Supabase user ids (the tokens' {@code sub}) allowed in. Anyone else with a
 *     valid token still gets 403.
 */
@Validated
@ConfigurationProperties("engineering.auth")
public record EngineeringAuthProperties(
        @NotBlank String issuer,
        @DefaultValue("authenticated") @NotBlank String audience,
        @NotEmpty Set<UUID> ownerIds) {

    /** The issuer without a trailing slash, as Supabase writes it in {@code iss}. */
    public String normalizedIssuer() {
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }

    /** Where the issuer publishes its signing keys. */
    public String jwksUri() {
        return normalizedIssuer() + "/.well-known/jwks.json";
    }
}
