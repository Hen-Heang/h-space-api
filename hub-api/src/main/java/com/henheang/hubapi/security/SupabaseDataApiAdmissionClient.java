package com.henheang.hubapi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.hubapi.config.HubSupabaseAuthProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admission from Supabase's Data API: {@code GET
 * <url>/rest/v1/kori_app_access?select=status&user_id=eq.<id>} with the project's publishable key
 * as {@code apikey} and the <em>caller's own</em> bearer token, so the table's row-level security
 * returns at most the caller's row. No service-role key, no admin API.
 *
 * <p>Fails closed: anything but a 200 with a JSON array of at most one row is {@code UNAVAILABLE};
 * no row, or a row whose status isn't exactly {@code approved}, is {@code DENIED}. Redirects aren't
 * followed, the reply is capped at {@value #MAX_BODY_BYTES} bytes, and nothing about the token or
 * the reply body is logged.
 */
public class SupabaseDataApiAdmissionClient implements AdmissionClient {

    private static final Logger log = LoggerFactory.getLogger(SupabaseDataApiAdmissionClient.class);

    static final String TABLE = "kori_app_access";
    static final int MAX_BODY_BYTES = 4 * 1024;

    private final HttpClient http;
    private final String baseUrl;
    private final String publishableKey;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public SupabaseDataApiAdmissionClient(
            HubSupabaseAuthProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = properties.normalizedUrl();
        this.publishableKey = properties.publishableKey().trim();
        this.timeout = properties.admissionTimeout();
        this.objectMapper = objectMapper;
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public Admission check(UUID userId, String bearerToken) {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        baseUrl
                                                + "/rest/v1/"
                                                + TABLE
                                                + "?select=status&user_id=eq."
                                                + userId
                                                + "&limit=2"))
                        .timeout(timeout)
                        .header("apikey", publishableKey)
                        .header("Authorization", "Bearer " + bearerToken)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
        try {
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    log.warn("Admission lookup failed: HTTP {}", response.statusCode());
                    return Admission.UNAVAILABLE;
                }
                byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) {
                    log.warn("Admission lookup failed: reply too large");
                    return Admission.UNAVAILABLE;
                }
                return interpret(objectMapper.readTree(bytes));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Admission lookup interrupted");
            return Admission.UNAVAILABLE;
        } catch (IOException | RuntimeException failure) {
            // Class name only: an exception message can carry a URL or a reply fragment.
            log.warn("Admission lookup failed: {}", failure.getClass().getSimpleName());
            return Admission.UNAVAILABLE;
        }
    }

    static Admission interpret(JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.size() > 1) {
            return Admission.UNAVAILABLE;
        }
        if (rows.isEmpty()) {
            return Admission.DENIED;
        }
        JsonNode status = rows.get(0).get("status");
        if (status == null || !status.isTextual()) {
            return Admission.UNAVAILABLE;
        }
        return "approved".equals(status.textValue()) ? Admission.APPROVED : Admission.DENIED;
    }
}
