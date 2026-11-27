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
import java.util.List;
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
 *       and {@code exp}/{@code nbf} must be valid (Spring's default 60 s clock skew).
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
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(normalizeIssuer(issuer)), audienceCheck);
    }
}
