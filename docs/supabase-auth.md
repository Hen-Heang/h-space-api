# `supabase-auth` — trusting the web app's Supabase session

Shared library (no bootJar, never run on its own). It lets a Spring module
accept the **Supabase Auth access token the H-Space web app already has**,
instead of issuing its own tokens. engineering-api uses it today; it is the
intended base for any future module the web app calls.

## Why

Since 2026-11-21 the web app signs in with Supabase Auth: several TOTP
devices per user, and an `aal` claim saying whether the session entered an
authenticator code. `security-api`'s own login can't express either, and the
old Supabase bridge needed the project's JWT secret and service-role key.
Verifying Supabase's tokens with its **published** keys needs no secret at
all and keeps one sign-in system.

## What it does

| Class | Job |
|---|---|
| `SupabaseJwtDecoders` | `jwkSource(issuer)` (lazy, cached, 3 s timeouts) and `decoder(keys, issuer, audience)`: **ES256/RS256 only** (HS256 and `alg: none` are refused before any key is tried), `iss` must equal the issuer (trailing slash ignored), `aud` must contain the audience (`authenticated`), `exp`/`nbf` with Spring's 60 s skew. Blank issuer or audience fails fast. |
| `SupabaseAuthenticationConverter` | Every valid token authenticates (so a caller without rights gets 403, not 401). Authorities: `SUPABASE_USER` when `sub` is a UUID; also `SUPABASE_MFA` when `aal` = `aal2`. The token's `role` claim is ignored. |
| `SupabaseSession` | `userId(jwt)` (the only source of a user id), `tryUserId`, `assuranceLevel`, `hasMfa`. |

## Using it in a module

```java
// build.gradle: implementation project(':supabase-auth')
@Bean JWKSource<SecurityContext> supabaseJwkSource(MyAuthProperties auth) throws MalformedURLException {
    return SupabaseJwtDecoders.jwkSource(auth.issuer());   // its own bean: tests swap in a local key
}
@Bean JwtDecoder jwtDecoder(JWKSource<SecurityContext> keys, MyAuthProperties auth) {
    return SupabaseJwtDecoders.decoder(keys, auth.issuer(), auth.audience());
}
// SecurityFilterChain: stateless, csrf off (bearer only), then
//   .requestMatchers("/api/x/**").hasAuthority(SupabaseAuthenticationConverter.USER)
//   .requestMatchers(sensitive).hasAuthority(SupabaseAuthenticationConverter.MFA)
//   .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)
//        .jwtAuthenticationConverter(new SupabaseAuthenticationConverter())))
// Controllers: UUID userId = SupabaseSession.userId(jwt); every query scoped by it.
```

Module-specific rules (engineering-api's owner allowlist) go in the module's
own converter on top of `SupabaseSession`; see
`engineering-api/.../security/OwnerAuthenticationConverter.java`.

## Configuration

Only public values: the issuer, e.g. `https://<project-ref>.supabase.co/auth/v1`
(keys are read from `<issuer>/.well-known/jwks.json`), and the audience
(`authenticated`). **Never** configure `SUPABASE_JWT_SECRET` or the service-role
key for this; a module that needs them is doing something else.

Precondition: the Supabase project must sign user tokens with asymmetric keys
(ES256/RS256). With only the legacy HS256 secret, every token is refused.

## Tests

`SupabaseJwtDecodersTest` and `SupabaseAuthenticationConverterTest` sign
tokens with EC/RSA keys generated in the test and serve the public halves as
an in-memory JWKS: no network, no database. They cover ES256 and RS256
acceptance, wrong issuer/audience, missing audience, expiry and the skew
boundary, HS256, `alg: none`, an unpublished key reusing a published `kid`,
garbage input, non-UUID or missing `sub`, and `aal1`/`aal2`/no `aal`.
