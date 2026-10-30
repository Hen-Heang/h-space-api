package com.henheang.securityapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Runs against a real Tomcat (MockMvc bypasses RemoteIpValve) to check that X-Forwarded-For can't
 * be used to dodge RateLimitingFilter. The test client connects from 127.0.0.1, which Tomcat trusts
 * as a proxy by default - i.e. this is the "behind Caddy/nginx on the same host" setup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitingClientIpIntegrationTest {

    private static final String LIMITED_PATH = "/api/auth/resend-verification";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort private int port;

    @Test
    void spoofedLeftmostForwardedFor_doesNotEscapeTheRealClientsBucket() throws Exception {
        // A proxy appends the address it saw, so the client controls only the
        // entries to the left of it.
        for (int i = 0; i < 5; i++) {
            assertThat(post("198.51.100." + i + ", 203.0.113.7")).isNotEqualTo(429);
        }
        assertThat(post("198.51.100.99, 203.0.113.7")).isEqualTo(429);
    }

    @Test
    void distinctClientsBehindTheProxy_getSeparateBuckets() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post("203.0.113.20")).isNotEqualTo(429);
        }
        assertThat(post("203.0.113.20")).isEqualTo(429);
        assertThat(post("203.0.113.21")).isNotEqualTo(429);
    }

    private int post(String forwardedFor) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + LIMITED_PATH))
                        .header("Content-Type", "application/json")
                        .header("X-Forwarded-For", forwardedFor)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
