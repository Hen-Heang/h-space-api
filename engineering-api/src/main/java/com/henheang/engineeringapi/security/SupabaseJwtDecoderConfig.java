package com.henheang.engineeringapi.security;

import com.henheang.engineeringapi.config.EngineeringAuthProperties;
import com.henheang.supabaseauth.SupabaseJwtDecoders;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.MalformedURLException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Verifies Supabase access tokens with the project's <em>published</em> keys, through the shared
 * {@code supabase-auth} module ({@link SupabaseJwtDecoders}: ES256/RS256 only, issuer, audience,
 * expiry). engineering-api holds no secret that could sign a token.
 *
 * <p>The key source is its own bean so tests can serve a key pair they made instead of Supabase's.
 */
@Configuration
public class SupabaseJwtDecoderConfig {

    @Bean
    public JWKSource<SecurityContext> supabaseJwkSource(EngineeringAuthProperties auth)
            throws MalformedURLException {
        return SupabaseJwtDecoders.jwkSource(auth.issuer());
    }

    @Bean
    public JwtDecoder jwtDecoder(
            JWKSource<SecurityContext> jwkSource, EngineeringAuthProperties auth) {
        return SupabaseJwtDecoders.decoder(jwkSource, auth.issuer(), auth.audience());
    }
}
