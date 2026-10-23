package com.henheang.securityapi.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Instead of allowing all origins with "*", specify allowed origins explicitly
        // For development; we can allow localhost with different ports
        config.setAllowedOriginPatterns(
                List.of(
                        "http://localhost:3000", // Next.js default
                        "http://localhost:4200", // Angular default
                        "http://localhost:8080", // Another common dev port
                        "http://127.0.0.1:3000",
                        "http://127.0.0.1:4200",
                        "http://127.0.0.1:8080"
                        // Add your production domains when deploying
                        // "https://yourdomain.com"
                        ));

        // Allow common HTTP methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Allow all headers
        config.setAllowedHeaders(
                Arrays.asList(
                        "Authorization",
                        "Content-Type",
                        "X-Requested-With",
                        "Accept",
                        "Origin",
                        "Access-Control-Request-Method",
                        "Access-Control-Request-Headers"));

        // Expose the Authorization header to the frontend
        config.setExposedHeaders(List.of("Authorization"));

        // Allow cookies - this requires specific origins, not "*"
        config.setAllowCredentials(true);

        // How long the preflight request can be cached
        config.setMaxAge(3600L);

        // Apply this configuration to all paths
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
