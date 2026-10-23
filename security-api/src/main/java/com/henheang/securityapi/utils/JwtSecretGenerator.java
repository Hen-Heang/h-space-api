package com.henheang.securityapi.utils;

import java.security.SecureRandom;
import java.util.Base64;

public class JwtSecretGenerator {
    public static void main(String[] args) {
        // Generate a 64-byte (512-bit) secret key
        SecureRandom secureRandom = new SecureRandom();
        byte[] secretBytes = new byte[64]; // 64 bytes = 512 bits
        secureRandom.nextBytes(secretBytes);

        // Encode to Base64 for use in application.yml
        String base64Secret = Base64.getEncoder().encodeToString(secretBytes);

        System.out.println("Generated JWT Secret (512 bits):");
        System.out.println(base64Secret);
        System.out.println(
                "Length: " + secretBytes.length + " bytes (" + (secretBytes.length * 8) + " bits)");

        // Verify it decodes to the correct length
        byte[] decoded = Base64.getDecoder().decode(base64Secret);
        System.out.println(
                "Verification - Decoded length: "
                        + decoded.length
                        + " bytes ("
                        + (decoded.length * 8)
                        + " bits)");
    }
}
