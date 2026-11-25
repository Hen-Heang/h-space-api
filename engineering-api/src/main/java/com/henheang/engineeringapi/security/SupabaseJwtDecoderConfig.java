package com.henheang.engineeringapi.security;

import com.henheang.engineeringapi.config.EngineeringAuthProperties;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies Supabase access tokens with the project's <em>published</em> keys. engineering-api holds
 * no secret that could sign a token - unlike security-api's retired Supabase bridge.
 *
 * <ul>
 *   <li><b>Signature:</b> ES256 or RS256 only, with a key from the issuer's JWKS. A token signed
 *       with HS256 - e.g. with Supabase's legacy shared secret, which still exists until it's
 *       rotated - is rejected before any key is tried.
 *   <li><b>Claims:</b> {@code iss} must equal the configured issuer, {@code aud} must contain the
 *       configured audience, and {@code exp}/{@code nbf} must be valid (60 s clock skew).
 * </ul>
 *
 * <p>Keys are fetched lazily on the first request and cached by Nimbus, so startup needs no
 * network.
 */
@Configuration
public class SupabaseJwtDecoderConfig {

    static final Set<JWSAlgorithm> ALLOWED_ALGORITHMS =
            Set.of(JWSAlgorithm.ES256, JWSAlgorithm.RS256);

    // Nimbus's defaults are 500 ms; a cold call from a sleeping container to
    // Supabase can need longer. Keys are cached, so this is rare.
    private static final int JWKS_CONNECT_TIMEOUT_MS = 3_000;
    private static final int JWKS_READ_TIMEOUT_MS = 3_000;
    private static final int JWKS_SIZE_LIMIT_BYTES = 50 * 1024;

    @Bean
    public JWKSource<SecurityContext> supabaseJwkSource(EngineeringAuthProperties auth)
            throws MalformedURLException {
        return JWKSourceBuilder.create(
                        URI.create(auth.jwksUri()).toURL(),
                        new DefaultResourceRetriever(
                                JWKS_CONNECT_TIMEOUT_MS,
                                JWKS_READ_TIMEOUT_MS,
                                JWKS_SIZE_LIMIT_BYTES))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            JWKSource<SecurityContext> jwkSource, EngineeringAuthProperties auth) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(ALLOWED_ALGORITHMS, jwkSource));

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(validator(auth));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(EngineeringAuthProperties auth) {
        OAuth2TokenValidator<Jwt> audience =
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD, aud -> aud != null && aud.contains(auth.audience()));
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(auth.normalizedIssuer()), audience);
    }
}
