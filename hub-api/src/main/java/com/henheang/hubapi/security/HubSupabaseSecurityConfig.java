package com.henheang.hubapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.hubapi.config.HubSupabaseAuthProperties;
import com.henheang.securityapi.security.JwtAuthenticationFilter;
import com.henheang.supabaseauth.SupabaseAuthenticationConverter;
import com.henheang.supabaseauth.SupabaseJwtDecoders;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.MalformedURLException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Auth V2 pilot chain (docs/auth-v2.md). Only when {@code hub.supabase-auth.enabled=true}; while
 * off this class contributes nothing and every route keeps security-api's chain.
 *
 * <p>It owns exactly {@link #PILOT_PATHS} and runs before security-api's catch-all chain, so a
 * pilot request is handled by this chain alone: Supabase bearer tokens only, no legacy-JWT
 * fallback, and no Supabase token ever reaches the legacy routes (they keep rejecting it). Four
 * separate gates, in order:
 *
 * <ol>
 *   <li>token: ES256/RS256 from the project's JWKS, issuer, audience, required exp (supabase-auth)
 *       - else 401;
 *   <li>subject: a UUID {@code sub} ({@code SUPABASE_USER}) - else 403;
 *   <li>admission: a current {@code approved} row ({@link HubAdmissionFilter}) - else 403, or 503
 *       if it can't be read;
 *   <li>assurance: {@code aal2} for the Inbox ({@code SUPABASE_MFA}, decision H12); the session
 *       probe accepts {@code aal1} so the web app can send the user to their code.
 * </ol>
 *
 * Ownership is the fifth: services scope every query by the verified user id.
 */
@Configuration
@ConditionalOnProperty(name = "hub.supabase-auth.enabled", havingValue = "true")
public class HubSupabaseSecurityConfig {

    static final String SESSION_PATH = "/api/hub/session";
    static final String INBOX_PATH = "/api/hub/inbox-items";
    static final String[] PILOT_PATHS = {SESSION_PATH, INBOX_PATH, INBOX_PATH + "/**"};

    /**
     * The project's published keys, fetched lazily and cached. Its own bean so tests can swap it.
     */
    @Bean
    public JWKSource<SecurityContext> hubSupabaseJwkSource(HubSupabaseAuthProperties properties)
            throws MalformedURLException {
        return SupabaseJwtDecoders.jwkSource(properties.validated().issuer());
    }

    @Bean
    public AdmissionClient hubAdmissionClient(
            HubSupabaseAuthProperties properties, ObjectMapper objectMapper) {
        return new SupabaseDataApiAdmissionClient(properties.validated(), objectMapper);
    }

    /**
     * security-api's {@code @Component} JWT filter would otherwise also be registered for every
     * request in the servlet container, i.e. run on pilot paths after this chain. On legacy routes
     * nothing changes: there it runs inside security-api's chain, which marks the request so the
     * container copy already skipped it.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> legacyJwtFilterContainerRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain hubSupabasePilotChain(
            HttpSecurity http,
            HubSupabaseAuthProperties properties,
            @Qualifier("hubSupabaseJwkSource") JWKSource<SecurityContext> keys,
            @Qualifier("hubAdmissionClient") AdmissionClient admissionClient,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper)
            throws Exception {
        HubSupabaseAuthProperties config = properties.validated();
        // Not a bean: a JwtDecoder bean would be picked up by Spring Boot's resource-server
        // auto-configuration for other chains.
        JwtDecoder decoder = SupabaseJwtDecoders.decoder(keys, config.issuer(), config.audience());
        PilotErrorWriter errors = new PilotErrorWriter(objectMapper);

        http.securityMatcher(PILOT_PATHS)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(HttpMethod.OPTIONS, PILOT_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, SESSION_PATH)
                                        .hasAuthority(HubAuthorities.MEMBER)
                                        .requestMatchers(INBOX_PATH, INBOX_PATH + "/**")
                                        .access(
                                                AuthorizationManagers.allOf(
                                                        AuthorityAuthorizationManager.hasAuthority(
                                                                HubAuthorities.MEMBER),
                                                        AuthorityAuthorizationManager.hasAuthority(
                                                                SupabaseAuthenticationConverter
                                                                        .MFA)))
                                        .anyRequest()
                                        .denyAll())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                                jwt ->
                                                        jwt.decoder(decoder)
                                                                .jwtAuthenticationConverter(
                                                                        new SupabaseAuthenticationConverter()))
                                        .authenticationEntryPoint(errors)
                                        .accessDeniedHandler(errors))
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(errors)
                                        .accessDeniedHandler(errors))
                .addFilterAfter(
                        new HubAdmissionFilter(admissionClient, errors),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
