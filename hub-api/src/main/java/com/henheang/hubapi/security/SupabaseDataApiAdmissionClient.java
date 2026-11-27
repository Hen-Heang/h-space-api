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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
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

    // Runs each lookup (headers + body) so the request thread can give up on time.
    // Daemon threads; a lookup abandoned at the deadline has its stream closed.
    private static final ExecutorService READERS =
            Executors.newCachedThreadPool(
                    task -> {
                        Thread thread = new Thread(task, "hub-admission-lookup");
                        thread.setDaemon(true);
                        return thread;
                    });

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
        // The request timeout only bounds the wait for response headers; a server that
        // then stalls the body would block a plain read forever. So the whole exchange -
        // headers and body - runs off the request thread, and the caller waits for at
        // most `timeout` in total. On expiry the stream is closed (ending the read) and
        // the answer is UNAVAILABLE.
        AtomicReference<InputStream> openBody = new AtomicReference<>();
        CompletableFuture<Admission> exchange =
                CompletableFuture.supplyAsync(() -> fetch(request, openBody), READERS);
        try {
            return exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException tooSlow) {
            log.warn("Admission lookup failed: no complete reply within {} ms", timeout.toMillis());
            closeQuietly(openBody.get());
            exchange.cancel(true);
            return Admission.UNAVAILABLE;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Admission lookup interrupted");
            closeQuietly(openBody.get());
            exchange.cancel(true);
            return Admission.UNAVAILABLE;
        } catch (ExecutionException | RuntimeException failure) {
            log.warn("Admission lookup failed: {}", failure.getClass().getSimpleName());
            return Admission.UNAVAILABLE;
        }
    }

    private Admission fetch(HttpRequest request, AtomicReference<InputStream> openBody) {
        try {
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                openBody.set(body);
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
            return Admission.UNAVAILABLE;
        } catch (IOException | RuntimeException failure) {
            // Class name only: an exception message can carry a URL or a reply fragment.
            // (A stream closed by the timeout above also lands here; that's expected.)
            log.debug("Admission lookup ended: {}", failure.getClass().getSimpleName());
            return Admission.UNAVAILABLE;
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // Already failing closed; nothing else to do.
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
