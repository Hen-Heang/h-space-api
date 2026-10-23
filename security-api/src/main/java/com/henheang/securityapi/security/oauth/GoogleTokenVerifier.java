package com.henheang.securityapi.security.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.exception.AuthException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Verifies a Google Sign-In ID token server-side: signature, issuer, audience, expiry. Fails closed
 * - if no client id is configured, every token is rejected rather than silently accepting an
 * unverified audience.
 */
@Component
public class GoogleTokenVerifier {

    @Value("${google.client-ids:}")
    private String clientIdsProperty;

    private GoogleIdTokenVerifier verifier;

    public GooglePrincipal verify(String idTokenString) {
        List<String> clientIds = configuredClientIds();
        if (clientIds.isEmpty()) {
            throw new AuthException(
                    ExitCode.OAUTH_PROVIDER_NOT_SUPPORTED,
                    "Google sign-in is not configured on this server (google.client-ids is empty)");
        }

        try {
            GoogleIdToken idToken = getVerifier(clientIds).verify(idTokenString);
            if (idToken == null) {
                throw new AuthException(ExitCode.OAUTH_ERROR, "Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new AuthException(ExitCode.OAUTH_EMAIL_NOT_VERIFIED);
            }

            return new GooglePrincipal(
                    payload.getSubject(), payload.getEmail(), (String) payload.get("name"));
        } catch (AuthException e) {
            throw e;
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            throw new AuthException(
                    ExitCode.OAUTH_ERROR, "Failed to verify Google ID token: " + e.getMessage());
        }
    }

    private synchronized GoogleIdTokenVerifier getVerifier(List<String> clientIds) {
        if (verifier == null) {
            verifier =
                    new GoogleIdTokenVerifier.Builder(
                                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                            .setAudience(clientIds)
                            .build();
        }
        return verifier;
    }

    private List<String> configuredClientIds() {
        if (!StringUtils.hasText(clientIdsProperty)) {
            return List.of();
        }
        return Arrays.stream(clientIdsProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    public record GooglePrincipal(String subject, String email, String name) {}
}
