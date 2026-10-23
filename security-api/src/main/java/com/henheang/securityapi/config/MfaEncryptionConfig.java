package com.henheang.securityapi.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Key for encrypting TOTP secrets at rest (see MfaSecretConverter). Same
// externalized-Base64 pattern as JwtConfig, but a separate key so rotating
// one never forces rotating the other.
@Configuration
@Getter
@Setter
public class MfaEncryptionConfig {

    @Value("${mfa.encryption-key}")
    private String encryptionKey;

    @Bean
    public SecretKey mfaSecretKey() {
        try {
            // Must decode to 32 bytes (256 bits) for AES-256.
            byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
            return new SecretKeySpec(decodedKey, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MFA encryption key", e);
        }
    }
}
