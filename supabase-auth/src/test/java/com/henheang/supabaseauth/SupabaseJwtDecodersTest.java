package com.henheang.supabaseauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * The decoder against key pairs made here: the test serves their public halves as the JWKS, so the
 * real algorithm, issuer, audience and expiry checks run and nothing contacts Supabase. All ids and
 * secrets are synthetic.
 */
class SupabaseJwtDecodersTest {

    private static final String ISSUER = "https://supabase-auth-test.supabase.invalid/auth/v1";
    private static final String USER_ID = "3c9d2f4e-8a1b-4c7d-9e2f-0a1b2c3d4e5f";

    private static final ECKey EC_KEY = ecKey("ec-key-1");
    private static final RSAKey RSA_KEY = rsaKey("rsa-key-1");
    // Same key id as the published EC key, but a different key: a forger who
    // copies the kid must still fail the signature check.
    private static final ECKey UNPUBLISHED_KEY = ecKey("ec-key-1");

    private final JwtDecoder decoder = decoderFor(ISSUER);

    @Test
    void es256Token_fromAPublishedKey_isAccepted() throws Exception {
        Jwt jwt = decoder.decode(es256(claims -> {}));

        assertThat(jwt.getSubject()).isEqualTo(USER_ID);
        assertThat(jwt.getClaimAsString("aal")).isEqualTo("aal2");
    }

    @Test
    void rs256Token_fromAPublishedKey_isAccepted() throws Exception {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                .keyID(RSA_KEY.getKeyID())
                                .type(JOSEObjectType.JWT)
                                .build(),
                        userClaims().build());
        jwt.sign(new RSASSASigner(RSA_KEY));

        assertThat(decoder.decode(jwt.serialize()).getSubject()).isEqualTo(USER_ID);
    }

    @Test
    void issuerWithATrailingSlash_stillMatchesSupabasesIss() throws Exception {
        assertThat(decoderFor(ISSUER + "/").decode(es256(claims -> {})).getSubject())
                .isEqualTo(USER_ID);
    }

    @Test
    void wrongIssuer_isRejected() {
        expectRejected(
                () -> es256(claims -> claims.issuer("https://other.supabase.invalid/auth/v1")));
    }

    @Test
    void wrongAudience_isRejected() {
        expectRejected(() -> es256(claims -> claims.audience("anon")));
    }

    @Test
    void missingAudience_isRejected() {
        expectRejected(() -> es256(claims -> claims.audience((String) null)));
    }

    @Test
    void expiredToken_isRejected() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        expectRejected(
                () ->
                        es256(
                                claims ->
                                        claims.issueTime(Date.from(past.minus(1, ChronoUnit.HOURS)))
                                                .expirationTime(Date.from(past))));
    }

    @Test
    void tokenExpiredWithinTheClockSkew_isAccepted_butNotBeyondIt() throws Exception {
        Instant now = Instant.now();
        String justExpired =
                es256(
                        claims ->
                                claims.issueTime(Date.from(now.minus(10, ChronoUnit.MINUTES)))
                                        .expirationTime(Date.from(now.minusSeconds(20))));
        assertThat(decoder.decode(justExpired).getSubject()).isEqualTo(USER_ID);

        expectRejected(
                () ->
                        es256(
                                claims ->
                                        claims.issueTime(
                                                        Date.from(
                                                                now.minus(10, ChronoUnit.MINUTES)))
                                                .expirationTime(Date.from(now.minusSeconds(120)))));
    }

    @Test
    void hs256Token_signedWithASharedSecret_isRejected() {
        // What a holder of Supabase's legacy JWT secret could mint.
        expectRejected(
                () ->
                        sign(
                                new MACSigner("synthetic-secret-only-for-this-test-0123456789"),
                                JWSAlgorithm.HS256,
                                null,
                                userClaims().build()));
    }

    @Test
    void unsignedAlgNoneToken_isRejected() {
        expectRejected(() -> new PlainJWT(userClaims().build()).serialize());
    }

    @Test
    void tokenSignedByAnUnpublishedKey_withTheSameKeyId_isRejected() {
        expectRejected(
                () ->
                        sign(
                                new ECDSASigner(UNPUBLISHED_KEY),
                                JWSAlgorithm.ES256,
                                UNPUBLISHED_KEY.getKeyID(),
                                userClaims().build()));
    }

    @Test
    void garbage_isRejected() {
        expectRejected(() -> "not.a.jwt");
    }

    @Test
    void jwksUri_isDerivedFromTheIssuer() {
        assertThat(SupabaseJwtDecoders.jwksUri(ISSUER + "/"))
                .isEqualTo(ISSUER + "/.well-known/jwks.json");
    }

    @Test
    void blankIssuerOrAudience_failsFast() {
        assertThatThrownBy(() -> SupabaseJwtDecoders.normalizeIssuer(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupabaseJwtDecoders.normalizeIssuer(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupabaseJwtDecoders.validator(ISSUER, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void expectRejected(ThrowingTokenSupplier token) {
        assertThatThrownBy(() -> decoder.decode(token.get())).isInstanceOf(JwtException.class);
    }

    @FunctionalInterface
    private interface ThrowingTokenSupplier {
        String get() throws Exception;
    }

    private static JwtDecoder decoderFor(String issuer) {
        return SupabaseJwtDecoders.decoder(
                new ImmutableJWKSet<>(
                        new JWKSet(java.util.List.of(EC_KEY.toPublicJWK(), RSA_KEY.toPublicJWK()))),
                issuer,
                SupabaseJwtDecoders.DEFAULT_AUDIENCE);
    }

    private static String es256(Consumer<JWTClaimsSet.Builder> customize) throws Exception {
        JWTClaimsSet.Builder claims = userClaims();
        customize.accept(claims);
        return sign(new ECDSASigner(EC_KEY), JWSAlgorithm.ES256, EC_KEY.getKeyID(), claims.build());
    }

    private static String sign(
            JWSSigner signer, JWSAlgorithm algorithm, String keyId, JWTClaimsSet claims)
            throws Exception {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(algorithm)
                                .keyID(keyId)
                                .type(JOSEObjectType.JWT)
                                .build(),
                        claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    /** A Supabase-shaped access token for a user signed in with an authenticator code. */
    private static JWTClaimsSet.Builder userClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(SupabaseJwtDecoders.DEFAULT_AUDIENCE)
                .subject(USER_ID)
                .claim("role", "authenticated")
                .claim("aal", "aal2")
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

    private static RSAKey rsaKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
