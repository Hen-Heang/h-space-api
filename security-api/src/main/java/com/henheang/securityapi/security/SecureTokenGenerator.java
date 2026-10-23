package com.henheang.securityapi.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

// Shared token source for every one-time, emailed security token (password
// reset, email verification, account unlock). 32 bytes of SecureRandom
// entropy, URL-safe base64 encoded so it can be dropped straight into a link
// without further escaping.
@Component
public class SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
