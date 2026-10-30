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
 * A caller that isn't a trusted proxy (here: loopback removed from the trusted list) is limited by
 * its own address, whatever X-Forwarded-For it sends - the pre-fix behaviour gave each forged value
 * a fresh bucket.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=192\\.0\\.2\\.1")
class RateLimitingUntrustedPeerIntegrationTest {

    private static final String LIMITED_PATH = "/api/auth/resend-verification";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort private int port;

    @Test
    void forwardedForFromUntrustedPeer_isIgnored() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post("198.51.100." + i)).isNotEqualTo(429);
        }
        assertThat(post("198.51.100.99")).isEqualTo(429);
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
