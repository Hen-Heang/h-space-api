package com.henheang.securityapi.security.supabase;

import com.henheang.commonapi.components.common.api.StatusCode;
import com.henheang.securityapi.exception.BusinessException;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Talks to Supabase Auth's admin API with the service-role key. Part of the temporary Supabase
 * bridge (see SupabaseBridgeService): every H-Space table still has a foreign key to {@code
 * auth.users(id)}, so each Spring user needs an {@code auth.users} row with the same UUID before
 * RLS-scoped writes can succeed. Delete together with the bridge once H-Space no longer reads
 * Supabase.
 */
@Component
public class SupabaseAdminClient {

    private final RestClient restClient;
    private final String serviceRoleKey;

    public SupabaseAdminClient(
            RestClient.Builder restClientBuilder,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.service-role-key:}") String serviceRoleKey) {
        this.serviceRoleKey = serviceRoleKey;
        this.restClient =
                restClientBuilder
                        .baseUrl(StringUtils.trimTrailingCharacter(supabaseUrl.trim(), '/'))
                        .build();
    }

    /** Whether {@code auth.users} already has a row with this id. */
    public boolean userExists(UUID userId) {
        try {
            restClient
                    .get()
                    .uri("/auth/v1/admin/users/{id}", userId)
                    .headers(this::authorize)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw upstreamError(e);
        } catch (RestClientException e) {
            throw upstreamError(e);
        }
    }

    /**
     * Creates the {@code auth.users} row with the Spring user's id. The email is marked confirmed
     * because the auth service already verified it (or Google did). Supabase answers 422 when the
     * email already belongs to a different id - an account from before the migration that has to be
     * imported instead, so that's reported as a conflict rather than retried.
     */
    public void createUser(UUID userId, String email) {
        try {
            restClient
                    .post()
                    .uri("/auth/v1/admin/users")
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("id", userId.toString(), "email", email, "email_confirm", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                throw new BusinessException(
                        StatusCode.CONFLICT,
                        "This email already has H-Space data from before the move to the new"
                                + " sign-in. It needs the account import before its data can"
                                + " load.");
            }
            throw upstreamError(e);
        } catch (RestClientException e) {
            throw upstreamError(e);
        }
    }

    // Legacy service_role keys are JWTs and go in both headers; the newer
    // sb_secret_ keys aren't JWTs and are rejected in Authorization, so they
    // only go in apikey.
    private void authorize(HttpHeaders headers) {
        headers.set("apikey", serviceRoleKey);
        if (!serviceRoleKey.startsWith("sb_")) {
            headers.setBearerAuth(serviceRoleKey);
        }
    }

    private BusinessException upstreamError(Exception e) {
        return new BusinessException(
                StatusCode.BAD_GATEWAY, "Couldn't reach Supabase Auth: " + e.getMessage());
    }
}
