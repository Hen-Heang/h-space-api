package com.henheang.engineeringapi.security;

import com.henheang.engineeringapi.config.EngineeringAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless bearer-token API. Everything under {@code /api/engineering/**} needs the owner
 * authority ({@link OwnerAuthenticationConverter}); health is public and detail-free; anything else
 * is denied. No sessions, cookies, CSRF tokens, form or basic login.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            EngineeringAuthProperties auth,
            ApiErrorWriter errors)
            throws Exception {
        OwnerAuthenticationConverter owner = new OwnerAuthenticationConverter(auth.ownerIds());

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        requests ->
                                requests
                                        // Liveness/readiness for Railway and the image's
                                        // HEALTHCHECK.
                                        .requestMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        // Served only when API_DOCS_ENABLED=true (off in prod).
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .requestMatchers("/api/engineering/**")
                                        .hasAuthority(OwnerAuthenticationConverter.OWNER)
                                        .anyRequest()
                                        .denyAll())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                                jwt ->
                                                        jwt.decoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(owner))
                                        .authenticationEntryPoint(errors)
                                        .accessDeniedHandler(errors))
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(errors)
                                        .accessDeniedHandler(errors));
        return http.build();
    }
}
