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
 * Runs on a real Tomcat (MockMvc skips its RemoteIpValve) to check how the client IP is resolved
 * behind a reverse proxy. The test client connects from 127.0.0.1, a trusted proxy address, so it
 * plays the proxy: it appends the "real" client as the last X-Forwarded-For entry, and anything
 * before that is what an untrusted client sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedClientIpIntegrationTest {

    private static final String LOGIN_BODY =
            "{\"identifier\":\"nobody@example.com\",\"password\":\"wrong-password\"}";

    @LocalServerPort private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void spoofedForwardedForEntriesDoNotBypassTheLoginRateLimit() throws Exception {
        // RateLimitingFilter allows 5 login attempts per minute per client.
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(login("198.51.100." + attempt + ", 203.0.113.7").statusCode())
                    .isNotEqualTo(429);
        }
        assertThat(login("198.51.100.99, 203.0.113.7").statusCode()).isEqualTo(429);
    }

    @Test
    void clientsForwardedByTheProxyAreLimitedSeparately() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            login("203.0.113.20");
        }
        assertThat(login("203.0.113.20").statusCode()).isEqualTo(429);
        assertThat(login("203.0.113.21").statusCode()).isNotEqualTo(429);
    }

    private HttpResponse<String> login(String forwardedFor) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("X-Forwarded-For", forwardedFor)
                        .POST(HttpRequest.BodyPublishers.ofString(LOGIN_BODY))
                        .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
