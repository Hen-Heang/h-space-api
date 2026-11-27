package com.henheang.supabaseauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds a {@link JwtDecoder} that verifies Supabase Auth access tokens with the project's
 * <em>published</em> keys. A module using it holds no secret that could sign a token - unlike
 * security-api's retired Supabase bridge.
 *
 * <ul>
 *   <li><b>Signature:</b> ES256 or RS256 only, with a key from the issuer's JWKS. A token signed
 *       with HS256 - e.g. with Supabase's legacy shared secret, which still exists until it's
 *       rotated - or an unsigned ({@code alg: none}) token is rejected before any key is tried.
 *   <li><b>Claims:</b> {@code iss} must equal the issuer, {@code aud} must contain the audience,
 *       {@code exp} must be present and {@code exp}/{@code nbf} valid (Spring's default 60 s clock
 *       skew).
 *   <li><b>Key location:</b> only the configured issuer's JWKS. Header-supplied {@code jku}/{@code
 *       x5u}/{@code jwk} are never followed. The issuer must be {@code https} (plain {@code http}
 *       only for a loopback host).
 * </ul>
 *
 * <p>Keys are fetched lazily on the first request and cached by Nimbus, so startup needs no
 * network. Which caller may do what is decided afterwards, by {@link
 * SupabaseAuthenticationConverter} and the module's own authorization rules.
 */
public final class SupabaseJwtDecoders {

    /** The only signature algorithms accepted. Supabase's asymmetric signing keys use these. */
    public static final Set<JWSAlgorithm> ALLOWED_ALGORITHMS =
            Set.of(JWSAlgorithm.ES256, JWSAlgorithm.RS256);

    /** The {@code aud} Supabase gives signed-in users' access tokens. */
    public static final String DEFAULT_AUDIENCE = "authenticated";

    // Nimbus's defaults are 500 ms; a cold call from a sleeping container to
    // Supabase can need longer. Keys are cached, so this is rare.
    private static final int JWKS_CONNECT_TIMEOUT_MS = 3_000;
    private static final int JWKS_READ_TIMEOUT_MS = 3_000;
    private static final int JWKS_SIZE_LIMIT_BYTES = 50 * 1024;

    private SupabaseJwtDecoders() {}

    /**
     * The issuer as Supabase writes it in {@code iss}: without a trailing slash.
     *
     * @param issuer the Supabase Auth issuer, e.g. {@code
     *     https://<project-ref>.supabase.co/auth/v1}
     * @throws IllegalArgumentException if it is blank
     */
    public static String normalizeIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("A Supabase Auth issuer is required");
        }
        String trimmed = issuer.trim();
        String normalized =
                trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        requireSecureIssuer(normalized);
        return normalized;
    }

    /**
     * Hardening (A1-H): the issuer - and so the JWKS URL derived from it - must be an absolute
     * {@code https} URL with a host and no query or fragment. Plain {@code http} is accepted only
     * for a loopback host ({@code localhost}, {@code 127.0.0.1}, {@code [::1]}), i.e. a local
     * Supabase stack or a test, never a remote key server an attacker on the path could answer.
     */
    static void requireSecureIssuer(String issuer) {
        URI uri;
        try {
            uri = new URI(issuer);
        } catch (java.net.URISyntaxException notAUri) {
            throw new IllegalArgumentException("The Supabase Auth issuer is not a valid URL");
        }
        String host = uri.getHost();
        if (!uri.isAbsolute() || host == null || host.isBlank()) {
            throw new IllegalArgumentException("The Supabase Auth issuer must be an absolute URL");
        }
        if (uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "The Supabase Auth issuer must not have a query, fragment or user info");
        }
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        boolean loopback =
                host.equalsIgnoreCase("localhost")
                        || host.equals("127.0.0.1")
                        || host.equals("[::1]")
                        || host.equals("::1");
        if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) {
            throw new IllegalArgumentException(
                    "The Supabase Auth issuer must use https (http only for a loopback host)");
        }
    }

    /** Where the issuer publishes its signing keys: {@code <issuer>/.well-known/jwks.json}. */
    public static String jwksUri(String issuer) {
        return normalizeIssuer(issuer) + "/.well-known/jwks.json";
    }

    /** The issuer's published keys, fetched lazily over HTTPS and cached. */
    public static JWKSource<SecurityContext> jwkSource(String issuer) throws MalformedURLException {
        return JWKSourceBuilder.create(
                        URI.create(jwksUri(issuer)).toURL(),
                        new DefaultResourceRetriever(
                                JWKS_CONNECT_TIMEOUT_MS,
                                JWKS_READ_TIMEOUT_MS,
                                JWKS_SIZE_LIMIT_BYTES))
                .build();
    }

    /**
     * A decoder that accepts only ES256/RS256 tokens signed by a key in {@code keys}, issued by
     * {@code issuer} for {@code audience}, and not expired.
     */
    public static JwtDecoder decoder(
            JWKSource<SecurityContext> keys, String issuer, String audience) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ALLOWED_ALGORITHMS, keys));

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(validator(issuer, audience));
        return decoder;
    }

    /** Issuer, audience and timestamp checks run after the signature is verified. */
    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("A token audience is required");
        }
        OAuth2TokenValidator<Jwt> audienceCheck =
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        // Hardening (A1-H): Spring's timestamp validator only checks exp when it is present, so a
        // token without one would never expire. Supabase always sets it; require it.
        OAuth2TokenValidator<Jwt> expiryRequired =
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull);
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(normalizeIssuer(issuer)),
                audienceCheck,
                expiryRequired);
    }
}
