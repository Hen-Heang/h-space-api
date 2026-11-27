package com.henheang.hubapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Auth V2 pilot chain end to end, with the pilot ON, against a synthetic Supabase on loopback:
 * the test serves its own signing key as the project's JWKS (so the real key fetch, decoder and
 * cache run) and plays the Data API's {@code kori_app_access} read. Inbox rows go to the real test
 * schema. Case ids are the Auth V2 test plan's (JWT / ACC / ISO / MFA / INBOX). Every id, key and
 * value here is synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupabasePilotIntegrationTest {

    private static final String SESSION = "/api/hub/session";
    private static final String INBOX = "/api/hub/inbox-items";
    private static final String PUBLISHABLE_KEY = "sb_publishable_synthetic_test_only";

    private static final ECKey SIGNING_KEY = newKey("pilot-test-key");
    private static final ECKey UNPUBLISHED_KEY = newKey("pilot-test-key");

    /** user id -> admission row status, or a failure mode: ERROR, MALFORMED, SLOW, TWO_ROWS. */
    private static final Map<String, String> ADMISSION = new ConcurrentHashMap<>();

    private static final AtomicInteger ADMISSION_CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_APIKEY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer SUPABASE = startSyntheticSupabase();
    private static final String URL = "http://127.0.0.1:" + SUPABASE.getAddress().getPort();
    private static final String ISSUER = URL + "/auth/v1";

    @DynamicPropertySource
    static void pilotOn(DynamicPropertyRegistry registry) {
        registry.add("hub.supabase-auth.enabled", () -> "true");
        registry.add("hub.supabase-auth.url", () -> URL);
        registry.add("hub.supabase-auth.publishable-key", () -> PUBLISHABLE_KEY);
        registry.add("hub.supabase-auth.admission-timeout", () -> "500ms");
    }

    @AfterAll
    static void stopSupabase() {
        SUPABASE.stop(0);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider legacyTokens;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void freshUsers() {
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        ADMISSION.put(alice.toString(), "approved");
        ADMISSION.put(bob.toString(), "approved");
    }

    // ---- session probe ------------------------------------------------------------------

    // ACC-01 / ACC-08: an approved aal1 user gets only their own summary, and is told the
    // Inbox needs MFA.
    @Test
    void sessionProbe_approvedAal1_returnsOwnSummaryOnly() throws Exception {
        String token = token(alice, "aal1");
        send(get(SESSION), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(alice.toString()))
                .andExpect(jsonPath("$.data.aal").value("aal1"))
                .andExpect(jsonPath("$.data.expiresAt", notNullValue()))
                .andExpect(jsonPath("$.data.mfaRequiredForInbox").value(true))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(token))));
        // The admission read used the caller's own token and only the publishable key.
        assertThat(LAST_AUTHORIZATION.get()).isEqualTo("Bearer " + token);
        assertThat(LAST_APIKEY.get()).isEqualTo(PUBLISHABLE_KEY);
    }

    @Test
    void sessionProbe_aal2_reportsAal2() throws Exception {
        send(get(SESSION), token(alice, "aal2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aal").value("aal2"));
    }

    // JWT-09 at the API: an unknown aal is never reported (or treated) as aal2.
    @Test
    void sessionProbe_unknownAal_isReportedAsAal1() throws Exception {
        send(get(SESSION), token(alice, "AAL2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aal").value("aal1"));
    }

    // ACC-02 / ACC-03: no admission row (e.g. an Orbit-only user of the shared project) or a
    // suspended one is 403, and nothing is written.
    @Test
    void notAdmittedOrSuspended_is403_andWritesNothing() throws Exception {
        UUID orbitOnly = UUID.randomUUID(); // no row at all
        ADMISSION.put(bob.toString(), "suspended");
        for (UUID user : List.of(orbitOnly, bob)) {
            expectForbidden(send(get(SESSION), token(user, "aal2")));
            expectForbidden(send(get(INBOX), token(user, "aal2")));
            expectForbidden(
                    send(
                            post(INBOX),
                            token(user, "aal2"),
                            Map.of("content", "x", "itemType", "note")));
            assertThat(appUserRows(user)).isZero();
        }
        assertThat(countItems()).isZero();
    }

    // ACC-02: an unknown status value is not membership.
    @Test
    void unknownAdmissionStatus_isNotMembership() throws Exception {
        ADMISSION.put(alice.toString(), "APPROVED");
        expectForbidden(send(get(SESSION), token(alice, "aal2")));
    }

    // ACC-06: the authority failing, timing out or answering nonsense is 503 - never admitted.
    @Test
    void admissionUnavailable_is503_neverAdmitted() throws Exception {
        for (String mode : List.of("ERROR", "MALFORMED", "SLOW", "TWO_ROWS")) {
            ADMISSION.put(alice.toString(), mode);
            send(get(SESSION), token(alice, "aal2"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status.code").value(50301))
                    .andExpect(
                            jsonPath("$.status.message")
                                    .value(PilotErrorWriter.UNAVAILABLE_MESSAGE))
                    .andExpect(jsonPath("$.status.requestId", notNullValue()));
            send(post(INBOX), token(alice, "aal2"), Map.of("content", "x", "itemType", "note"))
                    .andExpect(status().isServiceUnavailable());
        }
        assertThat(appUserRows(alice)).isZero();
    }

    // ACC-07: suspension takes effect on the very next request (no positive cache).
    @Test
    void suspension_appliesToTheNextRequest() throws Exception {
        String token = token(alice, "aal2");
        send(get(INBOX), token).andExpect(status().isOk());
        ADMISSION.put(alice.toString(), "suspended");
        expectForbidden(send(get(INBOX), token));
        ADMISSION.put(alice.toString(), "approved");
        send(get(INBOX), token).andExpect(status().isOk());
    }

    // ---- token checks at the HTTP boundary ----------------------------------------------

    // JWT-03 / JWT-02 / JWT-07 / JWT-05: bad tokens are 401 in the envelope, with no detail,
    // and are refused before any admission lookup.
    @Test
    void badTokens_are401_beforeAnyAdmissionLookup() throws Exception {
        int before = ADMISSION_CALLS.get();
        List<String> bad =
                List.of(
                        token(
                                alice,
                                "aal2",
                                c -> c.issuer("https://other.supabase.invalid/auth/v1")),
                        token(alice, "aal2", c -> c.audience("anon")),
                        token(
                                alice,
                                "aal2",
                                c ->
                                        c.expirationTime(
                                                Date.from(
                                                        Instant.now().minus(1, ChronoUnit.HOURS)))),
                        token(alice, "aal2", c -> c.expirationTime(null)),
                        signedBy(UNPUBLISHED_KEY, claims(alice, "aal2").build()),
                        hs256(claims(alice, "aal2").build()),
                        "not.a.jwt");
        for (String token : bad) {
            send(get(SESSION), token)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.status.code").value(40100))
                    .andExpect(
                            jsonPath("$.status.message")
                                    .value(PilotErrorWriter.UNAUTHORIZED_MESSAGE));
        }
        mvc.perform(get(INBOX)).andExpect(status().isUnauthorized());
        assertThat(ADMISSION_CALLS.get()).isEqualTo(before);
    }

    // JWT-08: a verified token whose sub isn't a UUID has no user authority: 403, and no
    // admission lookup is made for it.
    @Test
    void nonUuidSubject_is403_withoutAdmissionLookup() throws Exception {
        int before = ADMISSION_CALLS.get();
        String token = signedBy(SIGNING_KEY, claims(alice, "aal2").subject("service").build());
        expectForbidden(send(get(SESSION), token));
        expectForbidden(send(get(INBOX), token));
        assertThat(ADMISSION_CALLS.get()).isEqualTo(before);
    }

    // ---- isolation from security-api's own auth -----------------------------------------

    // ISO-01: a valid legacy Spring JWT is not a pilot credential (no fallback).
    @Test
    void legacySpringJwt_onPilotPaths_is401() throws Exception {
        String legacy = legacyTokens.generateToken(saveLegacyUser());
        send(get(SESSION), legacy).andExpect(status().isUnauthorized());
        send(get(INBOX), legacy).andExpect(status().isUnauthorized());
        send(post(INBOX), legacy, Map.of("content", "x", "itemType", "note"))
                .andExpect(status().isUnauthorized());
    }

    // ISO-02 / ISO-04: a Supabase token gets nothing on legacy-only routes; they keep their
    // own security-api behaviour.
    @Test
    void supabaseToken_onLegacyRoutes_getsNoLegacyAccess() throws Exception {
        String token = token(alice, "aal2");
        for (String legacyRoute :
                List.of("/api/auth/user", "/api/hub/vault/items", "/api/finance/categories")) {
            send(get(legacyRoute), token).andExpect(status().isUnauthorized());
        }
    }

    // ISO-05: inside the pilot chain anything not explicitly allowed is denied.
    @Test
    void unlistedMethodOnSessionPath_isDenied() throws Exception {
        expectForbidden(send(post(SESSION), token(alice, "aal2")));
    }

    // ISO-06: CORS for the configured origin only, never a wildcard with credentials.
    @Test
    void cors_allowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(
                        options(INBOX)
                                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "http://localhost:3000"));
        mvc.perform(
                        options(INBOX)
                                .header(HttpHeaders.ORIGIN, "https://evil.example")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // ISO-07: an ambient cookie is never a bearer credential.
    @Test
    void cookieWithoutBearer_is401() throws Exception {
        mvc.perform(
                        get(SESSION)
                                .cookie(
                                        new Cookie("sb-access-token", token(alice, "aal2")),
                                        new Cookie("koriai-auth", token(alice, "aal2"))))
                .andExpect(status().isUnauthorized());
    }

    // ---- Inbox: MFA and ownership -------------------------------------------------------

    // MFA-01: an admitted aal1 session gets the explicit MFA answer, and no data.
    @Test
    void inbox_admittedAal1_is403_withTheMfaMessage() throws Exception {
        String aal1 = token(alice, "aal1");
        for (MockHttpServletRequestBuilder request :
                List.of(get(INBOX), get(INBOX + "/" + UUID.randomUUID()))) {
            send(request, aal1)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status.code").value(40300))
                    .andExpect(
                            jsonPath("$.status.message")
                                    .value(PilotErrorWriter.MFA_REQUIRED_MESSAGE));
        }
        send(post(INBOX), aal1, Map.of("content", "x", "itemType", "note"))
                .andExpect(status().isForbidden());
        assertThat(appUserRows(alice)).isZero();
    }

    // MFA-02 / INBOX-02 / INBOX-07 / INBOX-08: admitted + aal2 works for a Supabase-only user
    // (no legacy users row), with the existing 200 envelopes for create and delete.
    @Test
    void inbox_admittedAal2_fullCycle_forASupabaseOnlyUser() throws Exception {
        assertThat(userRepository.findById(alice)).isEmpty();
        String token = token(alice, "aal2");

        String json =
                send(
                                post(INBOX),
                                token,
                                Map.of(
                                        "content",
                                        "  Synthetic capture  ",
                                        "itemType",
                                        "idea",
                                        "tags",
                                        List.of("pilot")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status.code").value(200))
                        .andExpect(jsonPath("$.data.content").value("Synthetic capture"))
                        .andExpect(jsonPath("$.data.status").value("inbox"))
                        .andExpect(jsonPath("$.data.source").value("manual"))
                        .andExpect(jsonPath("$.data.pinned").value(false))
                        .andExpect(jsonPath("$.data.version").doesNotExist())
                        .andExpect(jsonPath("$.data.userId").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = data(json).path("id").asText();
        assertThat(appUserRows(alice)).isEqualTo(1);

        send(get(INBOX), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        send(patch(INBOX + "/" + id), token, Map.of("pinned", true, "status", "processed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.status").value("processed"));
        send(delete(INBOX + "/" + id), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200));
        send(get(INBOX + "/" + id), token).andExpect(status().isNotFound());
        // A second create doesn't duplicate the anchor row.
        send(post(INBOX), token, Map.of("content", "again", "itemType", "note"))
                .andExpect(status().isOk());
        assertThat(appUserRows(alice)).isEqualTo(1);
    }

    // INBOX-04: Bob never reads, changes or deletes Alice's item, and lists only his own.
    @Test
    void inbox_otherUsersItems_are404_andNeverListed() throws Exception {
        String aliceToken = token(alice, "aal2");
        String bobToken = token(bob, "aal2");
        String id =
                data(send(
                                        post(INBOX),
                                        aliceToken,
                                        Map.of("content", "alice only", "itemType", "note"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .path("id")
                        .asText();

        send(get(INBOX + "/" + id), bobToken).andExpect(status().isNotFound());
        send(patch(INBOX + "/" + id), bobToken, Map.of("content", "taken"))
                .andExpect(status().isNotFound());
        send(delete(INBOX + "/" + id), bobToken).andExpect(status().isNotFound());
        send(get(INBOX), bobToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        send(get(INBOX + "/" + id), aliceToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("alice only"));
    }

    // ISO-08: error bodies carry no exception class, stack or library text.
    @Test
    void errorBodies_leakNoInternals() throws Exception {
        String body =
                send(get(SESSION), token(alice, "aal2", c -> c.audience("anon")))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body)
                .doesNotContainIgnoringCase("exception")
                .doesNotContain("Jwt")
                .doesNotContain("at com.");
    }

    // ---- helpers ------------------------------------------------------------------------

    private void expectForbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(40300))
                .andExpect(jsonPath("$.status.requestId", notNullValue()));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return send(request, token, null);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode data(String json) throws IOException {
        return objectMapper.readTree(json).path("data");
    }

    private int appUserRows(UUID id) {
        Integer n =
                jdbc.queryForObject(
                        "SELECT count(*) FROM app_users WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private int countItems() {
        Integer n =
                jdbc.queryForObject(
                        "SELECT count(*) FROM inbox_items WHERE user_id IN (?, ?)",
                        Integer.class,
                        alice,
                        bob);
        return n == null ? 0 : n;
    }

    private User saveLegacyUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        User user = new User();
        user.setEmail("pilot-" + suffix + "@example.com");
        user.setUsername("p" + suffix);
        user.setName("Pilot Tester");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private static String token(UUID user, String aal) {
        return token(user, aal, c -> {});
    }

    private static String token(UUID user, String aal, Consumer<JWTClaimsSet.Builder> customize) {
        JWTClaimsSet.Builder claims = claims(user, aal);
        customize.accept(claims);
        return signedBy(SIGNING_KEY, claims.build());
    }

    private static JWTClaimsSet.Builder claims(UUID user, String aal) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("authenticated")
                .subject(user.toString())
                .claim("role", "authenticated")
                .claim("aal", aal)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(10, ChronoUnit.MINUTES)));
    }

    private static String signedBy(ECKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.ES256)
                                    .keyID(key.getKeyID())
                                    .type(JOSEObjectType.JWT)
                                    .build(),
                            claims);
            jwt.sign(new ECDSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hs256(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner("synthetic-legacy-style-secret-0123456789-abcdef"));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ECKey newKey(String kid) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** JWKS + a minimal Data API read of kori_app_access, on loopback. */
    private static HttpServer startSyntheticSupabase() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/auth/v1/.well-known/jwks.json",
                    exchange ->
                            reply(exchange, 200, new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
            server.createContext(
                    "/rest/v1/kori_app_access",
                    exchange -> {
                        ADMISSION_CALLS.incrementAndGet();
                        LAST_APIKEY.set(exchange.getRequestHeaders().getFirst("apikey"));
                        LAST_AUTHORIZATION.set(
                                exchange.getRequestHeaders().getFirst("Authorization"));
                        String query = exchange.getRequestURI().getQuery();
                        String userId = query.replaceAll(".*user_id=eq\\.([0-9a-f-]{36}).*", "$1");
                        String mode = ADMISSION.get(userId);
                        if (mode == null) {
                            reply(exchange, 200, "[]");
                            return;
                        }
                        switch (mode) {
                            case "ERROR" -> reply(exchange, 500, "{\"message\":\"boom\"}");
                            case "MALFORMED" -> reply(exchange, 200, "{\"status\":\"approved\"}");
                            case "TWO_ROWS" ->
                                    reply(
                                            exchange,
                                            200,
                                            "[{\"status\":\"approved\"},{\"status\":\"approved\"}]");
                            case "SLOW" -> {
                                try {
                                    Thread.sleep(1_500);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                reply(exchange, 200, "[{\"status\":\"approved\"}]");
                            }
                            default -> reply(exchange, 200, "[{\"status\":\"" + mode + "\"}]");
                        }
                    });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException clientGone) {
            // The caller timed out first (SLOW); nothing to do.
        } finally {
            exchange.close();
        }
    }
}
