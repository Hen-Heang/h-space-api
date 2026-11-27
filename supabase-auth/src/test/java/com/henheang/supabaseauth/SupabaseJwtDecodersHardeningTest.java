package com.henheang.supabaseauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * A1-H hardening, kept apart from {@link SupabaseJwtDecodersTest} (the behaviour-preserving
 * extraction): required {@code exp}, timestamp boundaries, audience arrays, an https issuer, no
 * header-supplied key URLs, and key rotation / JWKS outage against a real (loopback) key server.
 * Test-case ids refer to the Auth V2 test plan (JWT-xx). Keys, ids and hosts are synthetic.
 */
class SupabaseJwtDecodersHardeningTest {

    private static final String ISSUER = "https://supabase-auth-test.supabase.invalid/auth/v1";
    private static final String USER_ID = "3c9d2f4e-8a1b-4c7d-9e2f-0a1b2c3d4e5f";

    private static final ECKey KEY_A = ecKey("key-a");
    private static final ECKey KEY_B = ecKey("key-b");

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private final JwtDecoder decoder =
            SupabaseJwtDecoders.decoder(
                    new ImmutableJWKSet<>(new JWKSet(KEY_A.toPublicJWK())),
                    ISSUER,
                    SupabaseJwtDecoders.DEFAULT_AUDIENCE);

    // JWT-05: exp is required, not merely checked when present.
    @Test
    void tokenWithoutExp_isRejected() {
        expectRejected(() -> sign(KEY_A, claims -> claims.expirationTime(null)));
    }

    // JWT-03: a missing issuer is not "any issuer".
    @Test
    void tokenWithoutIssuer_isRejected() {
        expectRejected(() -> sign(KEY_A, claims -> claims.issuer(null)));
    }

    // JWT-04: an audience array is fine as long as it contains the configured audience.
    @Test
    void audienceArray_containingTheAudience_isAccepted_otherwiseRejected() throws Exception {
        assertThat(
                        decoder.decode(
                                        sign(
                                                KEY_A,
                                                claims ->
                                                        claims.audience(
                                                                List.of(
                                                                        "other",
                                                                        SupabaseJwtDecoders
                                                                                .DEFAULT_AUDIENCE))))
                                .getSubject())
                .isEqualTo(USER_ID);
        expectRejected(() -> sign(KEY_A, claims -> claims.audience(List.of("anon", "other"))));
    }

    // JWT-06: a not-before in the future is honoured with the same 60 s skew as exp.
    @Test
    void notBefore_inTheFuture_isRejected_butWithinTheSkewAccepted() throws Exception {
        Instant now = Instant.now();
        assertThat(
                        decoder.decode(
                                        sign(
                                                KEY_A,
                                                claims ->
                                                        claims.notBeforeTime(
                                                                Date.from(now.plusSeconds(20)))))
                                .getSubject())
                .isEqualTo(USER_ID);
        expectRejected(
                () ->
                        sign(
                                KEY_A,
                                claims ->
                                        claims.notBeforeTime(
                                                Date.from(now.plus(5, ChronoUnit.MINUTES)))));
    }

    // JWT-06: a malformed timestamp is a rejection, not a crash.
    @Test
    void malformedExp_isRejected() {
        expectRejected(() -> sign(KEY_A, claims -> claims.claim("exp", "tomorrow")));
    }

    // JWT-11: the issuer (and so the JWKS URL) must be https; http only on loopback.
    @Test
    void insecureOrMalformedIssuer_failsFast() {
        for (String bad :
                List.of(
                        "http://attacker.example/auth/v1",
                        "ftp://example.supabase.co/auth/v1",
                        "example.supabase.co/auth/v1",
                        "https:///auth/v1",
                        "https://example.supabase.co/auth/v1?x=1",
                        "https://user@example.supabase.co/auth/v1")) {
            assertThatThrownBy(() -> SupabaseJwtDecoders.normalizeIssuer(bad))
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SupabaseJwtDecoders.jwkSource(bad))
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatCode(() -> SupabaseJwtDecoders.normalizeIssuer("http://127.0.0.1:54321/auth/v1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SupabaseJwtDecoders.normalizeIssuer("http://localhost:54321/auth/v1"))
                .doesNotThrowAnyException();
    }

    // JWT-12: a token naming its own key server (jku), or embedding its own key (jwk), is
    // verified against the configured keys only - and the named server is never contacted.
    @Test
    void headerSuppliedKeyLocations_areNeverFollowed() throws Exception {
        AtomicInteger attackerHits = new AtomicInteger();
        ECKey attackerKey = ecKey("key-a");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    attackerHits.incrementAndGet();
                    byte[] body =
                            new JWKSet(attackerKey.toPublicJWK())
                                    .toString()
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        URI attackerJwks =
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks.json");

        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256)
                                .keyID(attackerKey.getKeyID())
                                .jwkURL(attackerJwks)
                                .jwk(attackerKey.toPublicJWK())
                                .x509CertURL(attackerJwks)
                                .type(JOSEObjectType.JWT)
                                .build(),
                        userClaims().build());
        jwt.sign(new ECDSASigner(attackerKey));

        assertThatThrownBy(() -> decoder.decode(jwt.serialize())).isInstanceOf(JwtException.class);
        assertThat(attackerHits).hasValue(0);
    }

    // JWT-13: key rotation, unknown kid and a JWKS outage, against a real key server on
    // loopback (so the library's own JWKSourceBuilder cache and retriever are what runs).
    @Test
    void keyRotation_unknownKid_andJwksOutage() throws Exception {
        AtomicReference<JWKSet> published = new AtomicReference<>(new JWKSet(KEY_A.toPublicJWK()));
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger fetches = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/auth/v1/.well-known/jwks.json",
                exchange -> {
                    fetches.incrementAndGet();
                    byte[] body = published.get().toString().getBytes(StandardCharsets.UTF_8);
                    if (status.get() != 200) {
                        exchange.sendResponseHeaders(status.get(), -1);
                    } else {
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                    }
                    exchange.close();
                });
        server.start();
        String issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/v1";
        JWKSource<SecurityContext> keys = SupabaseJwtDecoders.jwkSource(issuer);
        JwtDecoder live =
                SupabaseJwtDecoders.decoder(keys, issuer, SupabaseJwtDecoders.DEFAULT_AUDIENCE);

        // Lazy: building the decoder contacted nothing.
        assertThat(fetches).hasValue(0);

        // Known key works, and is cached.
        assertThat(live.decode(sign(KEY_A, issuer, c -> {})).getSubject()).isEqualTo(USER_ID);
        assertThat(live.decode(sign(KEY_A, issuer, c -> {})).getSubject()).isEqualTo(USER_ID);
        int afterKnown = fetches.get();
        assertThat(afterKnown).isEqualTo(1);

        // Rotation: a new kid triggers a refetch and then verifies.
        published.set(new JWKSet(List.of(KEY_A.toPublicJWK(), KEY_B.toPublicJWK())));
        assertThat(live.decode(sign(KEY_B, issuer, c -> {})).getSubject()).isEqualTo(USER_ID);
        assertThat(fetches.get()).isGreaterThan(afterKnown);

        // A kid that is published nowhere is refused, not trusted.
        ECKey unknown = ecKey("key-unknown");
        assertThatThrownBy(() -> live.decode(sign(unknown, issuer, c -> {})))
                .isInstanceOf(JwtException.class);

        // Outage: a kid not in the cache cannot be fetched -> refused (fail closed), never a
        // fallback to some other key or to no signature check.
        status.set(503);
        ECKey whileDown = ecKey("key-c");
        assertThatThrownBy(() -> live.decode(sign(whileDown, issuer, c -> {})))
                .isInstanceOf(JwtException.class);
    }

    private void expectRejected(ThrowingTokenSupplier token) {
        assertThatThrownBy(() -> decoder.decode(token.get())).isInstanceOf(JwtException.class);
    }

    @FunctionalInterface
    private interface ThrowingTokenSupplier {
        String get() throws Exception;
    }

    private static String sign(ECKey key, Consumer<JWTClaimsSet.Builder> customize)
            throws Exception {
        return sign(key, ISSUER, customize);
    }

    private static String sign(ECKey key, String issuer, Consumer<JWTClaimsSet.Builder> customize)
            throws Exception {
        JWTClaimsSet.Builder claims = userClaims().issuer(issuer);
        customize.accept(claims);
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256)
                                .keyID(key.getKeyID())
                                .type(JOSEObjectType.JWT)
                                .build(),
                        claims.build());
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder userClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(SupabaseJwtDecoders.DEFAULT_AUDIENCE)
                .subject(USER_ID)
                .claim("role", "authenticated")
                .claim("aal", "aal1")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(10, ChronoUnit.MINUTES)));
    }

    private static ECKey ecKey(String keyId) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
