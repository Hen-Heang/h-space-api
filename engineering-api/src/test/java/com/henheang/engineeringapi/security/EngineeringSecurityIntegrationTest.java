package com.henheang.engineeringapi.security;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The whole security chain against a key pair made here: the test serves its public half as the
 * JWKS, so the real decoder (algorithms, issuer, audience, expiry) and the owner rule run
 * unchanged, and nothing contacts Supabase. All ids and secrets are synthetic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EngineeringSecurityIntegrationTest.PublishedTestKey.class)
class EngineeringSecurityIntegrationTest {

    // Must match application-test.yml.
    private static final String ISSUER = "https://engineering-test.supabase.invalid/auth/v1";
    private static final UUID OWNER = UUID.fromString("0b0e5c2a-7d3c-4c8e-9a51-2f6d8e4b1a01");
    private static final UUID SOMEONE_ELSE =
            UUID.fromString("7f3a1c9e-2b4d-4e6f-8a0b-1c2d3e4f5a6b");
    private static final String SESSION = "/api/engineering/session";

    private static final ECKey PUBLISHED_KEY = newKey("test-key-1");
    // Same key id as the published one, but a different key: a forger who
    // copies the kid must still fail the signature check.
    private static final ECKey UNPUBLISHED_KEY = newKey("test-key-1");

    @TestConfiguration
    static class PublishedTestKey {
        @Bean
        @Primary
        JWKSource<SecurityContext> testJwkSource() {
            return new ImmutableJWKSet<>(new JWKSet(PUBLISHED_KEY.toPublicJWK()));
        }
    }

    @Autowired private MockMvc mvc;

    @Test
    void owner_withAal2Session_getsTheirSession() throws Exception {
        call(token(claims -> {}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.ownerId").value(OWNER.toString()))
                .andExpect(jsonPath("$.data.assuranceLevel").value("aal2"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void noToken_is401_inTheEnvelope_withRequestId() throws Exception {
        mvc.perform(get(SESSION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status.code").value(40100))
                .andExpect(jsonPath("$.status.message").value(ApiErrorWriter.UNAUTHORIZED_MESSAGE))
                .andExpect(jsonPath("$.status.requestId", notNullValue()));
    }

    @Test
    void owner_withPasswordOnlySession_is403() throws Exception {
        expectForbidden(call(token(claims -> claims.claim("aal", "aal1"))));
    }

    @Test
    void ownerToken_withoutAalClaim_is403() throws Exception {
        expectForbidden(call(token(claims -> claims.claim("aal", null))));
    }

    @Test
    void someoneElse_evenWithAal2_is403() throws Exception {
        expectForbidden(call(token(claims -> claims.subject(SOMEONE_ELSE.toString()))));
    }

    @Test
    void subjectThatIsNotAUuid_is403() throws Exception {
        expectForbidden(call(token(claims -> claims.subject("not-a-uuid"))));
    }

    @Test
    void wrongIssuer_is401() throws Exception {
        expectUnauthorized(
                call(token(claims -> claims.issuer("https://other.supabase.invalid/auth/v1"))));
    }

    @Test
    void wrongAudience_is401() throws Exception {
        expectUnauthorized(call(token(claims -> claims.audience("anon"))));
    }

    @Test
    void expiredToken_is401() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        expectUnauthorized(
                call(
                        token(
                                claims ->
                                        claims.issueTime(Date.from(past.minus(1, ChronoUnit.HOURS)))
                                                .expirationTime(Date.from(past)))));
    }

    @Test
    void hs256Token_signedWithASharedSecret_is401() throws Exception {
        // What a holder of Supabase's legacy JWT secret could mint.
        SignedJWT forged =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                        ownerClaims().build());
        forged.sign(new MACSigner("synthetic-secret-only-for-this-test-0123456789"));
        expectUnauthorized(call(forged.serialize()));
    }

    @Test
    void unsignedAlgNoneToken_is401() throws Exception {
        expectUnauthorized(call(new PlainJWT(ownerClaims().build()).serialize()));
    }

    @Test
    void tokenSignedByAnUnpublishedKey_withTheSameKeyId_is401() throws Exception {
        expectUnauthorized(call(token(UNPUBLISHED_KEY, claims -> {})));
    }

    @Test
    void health_isPublic_andAnythingOutsideTheApi_isDenied() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/somewhere-else")).andExpect(status().isUnauthorized());
    }

    @Test
    void owner_onAnUnknownRoute_gets404_inTheEnvelope() throws Exception {
        mvc.perform(
                        get("/api/engineering/nothing-here")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(c -> {})))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.code").value(40400))
                .andExpect(jsonPath("$.status.requestId", notNullValue()));
    }

    private ResultActions call(String bearerToken) throws Exception {
        return mvc.perform(get(SESSION).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken));
    }

    private static void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(40100));
    }

    private static void expectForbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(40300))
                .andExpect(jsonPath("$.status.message").value(ApiErrorWriter.FORBIDDEN_MESSAGE));
    }

    private static String token(Consumer<JWTClaimsSet.Builder> customize) throws Exception {
        return token(PUBLISHED_KEY, customize);
    }

    private static String token(ECKey key, Consumer<JWTClaimsSet.Builder> customize)
            throws Exception {
        JWTClaimsSet.Builder claims = ownerClaims();
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

    /** A Supabase-shaped access token for the owner, signed in with an authenticator code. */
    private static JWTClaimsSet.Builder ownerClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience("authenticated")
                .subject(OWNER.toString())
                .claim("role", "authenticated")
                .claim("aal", "aal2")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(10, ChronoUnit.MINUTES)));
    }

    private static ECKey newKey(String keyId) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
