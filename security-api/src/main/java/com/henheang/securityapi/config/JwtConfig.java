package com.henheang.securityapi.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private String expirationTime;

    @Bean
    public SecretKey jwtSecretKey() {
        try {
            // Decode the externalized Base64 secret (jwt.secret). It must decode to
            // at least 64 bytes (512 bits) to satisfy the HS512 signing algorithm.
            byte[] decodedKey = Base64.getDecoder().decode(secret);
            return new SecretKeySpec(decodedKey, "HmacSHA512");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JWT secret key", e);
        }
    }
}
