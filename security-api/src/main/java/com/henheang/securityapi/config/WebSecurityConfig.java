package com.henheang.securityapi.config;

import com.henheang.securityapi.security.*;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint unauthorizedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(
                        exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(
                        headers ->
                                headers
                                        // Force HTTPS on every future request once a browser sees
                                        // it once over TLS
                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                        // Swagger UI is served from this same app (permitAll,
                                        // /swagger-ui/**)
                                        // and needs its own inline script/style - "default-src
                                        // 'none'" would
                                        // break it, so this is the tightest policy that still lets
                                        // it load.
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                                                                        + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"))
                                        .frameOptions(frame -> frame.deny())
                                        .contentTypeOptions(contentType -> {})
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                org.springframework.security.web
                                                                        .header.writers
                                                                        .ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .NO_REFERRER)))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Allow OPTIONS requests for CORS preflight
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        // More specific than the "/api/auth/**" permitAll below -
                                        // order matters,
                                        // the first matching rule wins - these actually need a
                                        // valid principal.
                                        .requestMatchers("/api/auth/user")
                                        .authenticated()
                                        .requestMatchers(
                                                "/api/auth/mfa/setup",
                                                "/api/auth/mfa/enable",
                                                "/api/auth/mfa/disable",
                                                "/api/auth/mfa/backup-codes/regenerate")
                                        .authenticated()
                                        .requestMatchers("/api/auth/**", "/api/v1/auth/**")
                                        .permitAll()
                                        .requestMatchers("/api/public/**")
                                        .permitAll()
                                        .requestMatchers("/api/users/**")
                                        .authenticated()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/v1/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/swagger-resources/**",
                                                "/webjars/**",
                                                "/api-docs/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated());
        // Google sign-in is handled via ID-token verification (POST
        // /api/auth/oauth2/google, see GoogleTokenVerifier) rather than the
        // authorization-code/redirect flow, so no .oauth2Login(...) here.

        // Rate limit brute-forceable endpoints before anything else runs
        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        // Add JWT filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Specify allowed origins explicitly
        configuration.setAllowedOriginPatterns(
                List.of(
                        "http://localhost:3000",
                        "http://localhost:4200",
                        "http://localhost:8080",
                        "http://127.0.0.1:3000",
                        "http://127.0.0.1:4200",
                        "http://127.0.0.1:8080",
                        "https://yourdomain.com"));
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(
                Arrays.asList(
                        "Authorization",
                        "Content-Type",
                        "X-Requested-With",
                        "Accept",
                        "Origin",
                        "Access-Control-Request-Method",
                        "Access-Control-Request-Headers"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfiguration) throws Exception {
        return authConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Wires CustomPermissionEvaluator into @PreAuthorize("hasPermission(...)")
    // expressions. Declared static (a factory method, not an instance bean)
    // per Spring Security's guidance for method-security infrastructure
    // beans - @EnableMethodSecurity's AOP advisors are created very early,
    // and a non-static bean here can force this whole @Configuration class
    // to be instantiated prematurely.
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            CustomPermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler =
                new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
