package com.henheang.securityapi.security;

import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.expiration}")
    private String jwtExpirationString;

    @Autowired private SecretKey jwtSecretKey;

    public String generateToken(Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            return generateToken(userPrincipal.getId(), null);
        } catch (Exception e) {
            throw new AuthException(
                    ExitCode.JWT_CONFIGURATION_ERROR,
                    "Failed to generate JWT token: " + e.getMessage());
        }
    }

    public String generateToken(User user) {
        try {
            return generateToken(user.getId(), null);
        } catch (Exception e) {
            throw new AuthException(
                    ExitCode.JWT_CONFIGURATION_ERROR,
                    "Failed to generate JWT token: " + e.getMessage());
        }
    }

    public String generateToken(UUID userId, String tokenType) {
        return generateToken(userId, tokenType, Duration.parse(jwtExpirationString));
    }

    public String generateToken(UUID userId, String tokenType, Duration duration) {
        try {
            Map<String, Object> claims = new HashMap<>();
            if (tokenType != null) {
                claims.put("token_type", tokenType);
            }

            Instant now = Instant.now();
            Instant expiryDate = now.plus(duration);

            return Jwts.builder()
                    .setClaims(claims)
                    .setId(UUID.randomUUID().toString())
                    .setSubject(userId.toString())
                    .setIssuedAt(Date.from(now))
                    .setExpiration(Date.from(expiryDate))
                    .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                    .compact();
        } catch (Exception e) {
            throw new AuthException(
                    ExitCode.JWT_CONFIGURATION_ERROR,
                    "Failed to generate JWT token: " + e.getMessage());
        }
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    public Instant getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public String getTokenTypeFromToken(String token) {
        return (String) parseClaims(token).get("token_type");
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            throw new AuthException(
                    ExitCode.TOKEN_INVALID, "Failed to parse JWT claims: " + e.getMessage());
        }
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private SecretKey getSigningKey() {
        return jwtSecretKey;
    }
}
