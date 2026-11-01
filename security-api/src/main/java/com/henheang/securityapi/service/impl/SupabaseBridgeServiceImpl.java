package com.henheang.securityapi.service.impl;

import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.BusinessException;
import com.henheang.securityapi.payload.SupabaseTokenResponse;
import com.henheang.securityapi.security.supabase.SupabaseAdminClient;
import com.henheang.securityapi.service.SupabaseBridgeService;
import com.henheang.securityapi.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Signs tokens with the Supabase project's legacy JWT secret (HS256), carrying the claims PostgREST
 * needs: {@code sub} = the user's UUID (what {@code auth.uid()} returns in RLS policies), {@code
 * role}/{@code aud} = {@code authenticated}. Before the first token for a user, makes sure {@code
 * auth.users} has a row with that UUID, since H-Space's tables reference it.
 *
 * <p>Disabled - every call fails with 503 - unless {@code supabase.url}, {@code
 * supabase.jwt-secret} and {@code supabase.service-role-key} are all set.
 */
@Service
public class SupabaseBridgeServiceImpl implements SupabaseBridgeService {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseBridgeServiceImpl.class);
    private static final String AUTHENTICATED = "authenticated";

    // Logout revokes this service's tokens but can't reach one already handed
    // to Supabase, so it stays usable until it expires - keep that short.
    static final Duration MAX_TOKEN_TTL = Duration.ofMinutes(15);

    private final UserService userService;
    private final SupabaseAdminClient supabaseAdminClient;
    private final String supabaseUrl;
    private final String jwtSecret;
    private final String serviceRoleKey;
    private final Duration tokenTtl;
    private final Clock clock;

    // Users already known to exist in auth.users, so steady-state token
    // requests don't call the admin API. Lost on restart, which only costs one
    // lookup per user.
    private final Set<UUID> provisionedUsers = ConcurrentHashMap.newKeySet();

    @Autowired
    public SupabaseBridgeServiceImpl(
            UserService userService,
            SupabaseAdminClient supabaseAdminClient,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.jwt-secret:}") String jwtSecret,
            @Value("${supabase.service-role-key:}") String serviceRoleKey,
            @Value("${supabase.bridge-token-ttl:PT10M}") Duration tokenTtl) {
        this(
                userService,
                supabaseAdminClient,
                supabaseUrl,
                jwtSecret,
                serviceRoleKey,
                tokenTtl,
                Clock.systemUTC());
    }

    SupabaseBridgeServiceImpl(
            UserService userService,
            SupabaseAdminClient supabaseAdminClient,
            String supabaseUrl,
            String jwtSecret,
            String serviceRoleKey,
            Duration tokenTtl,
            Clock clock) {
        this.userService = userService;
        this.supabaseAdminClient = supabaseAdminClient;
        this.supabaseUrl = supabaseUrl;
        this.jwtSecret = jwtSecret;
        this.serviceRoleKey = serviceRoleKey;
        this.tokenTtl = capped(tokenTtl);
        this.clock = clock;
        logBridgeState(missingSettings(supabaseUrl, jwtSecret, serviceRoleKey));
    }

    // Names only - never the values. A bridge that's off fails every
    // /api/auth/supabase-token call with 503, so say at startup which setting
    // didn't arrive rather than leave it to be guessed.
    private static void logBridgeState(List<String> missing) {
        if (missing.isEmpty()) {
            logger.info("Supabase bridge on");
        } else if (missing.size() == 3) {
            logger.info("Supabase bridge off: none of {} is set", missing);
        } else {
            logger.warn("Supabase bridge off: missing {}", missing);
        }
    }

    static List<String> missingSettings(
            String supabaseUrl, String jwtSecret, String serviceRoleKey) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(supabaseUrl)) {
            missing.add("supabase.url (SUPABASE_URL)");
        }
        if (!StringUtils.hasText(jwtSecret)) {
            missing.add("supabase.jwt-secret (SUPABASE_JWT_SECRET)");
        }
        if (!StringUtils.hasText(serviceRoleKey)) {
            missing.add("supabase.service-role-key (SUPABASE_SERVICE_ROLE_KEY)");
        }
        return missing;
    }

    private static Duration capped(Duration tokenTtl) {
        if (tokenTtl.compareTo(MAX_TOKEN_TTL) <= 0) {
            return tokenTtl;
        }
        logger.warn(
                "supabase.bridge-token-ttl {} is longer than the {} cap; using the cap",
                tokenTtl,
                MAX_TOKEN_TTL);
        return MAX_TOKEN_TTL;
    }

    @Override
    public boolean isEnabled() {
        return missingSettings(supabaseUrl, jwtSecret, serviceRoleKey).isEmpty();
    }

    @Override
    public SupabaseTokenResponse issueToken(UUID userId) {
        if (!isEnabled()) {
            throw new BusinessException(
                    StatusCode.SERVICE_UNAVAILABLE,
                    "The Supabase bridge isn't configured (SUPABASE_URL, SUPABASE_JWT_SECRET,"
                            + " SUPABASE_SERVICE_ROLE_KEY).");
        }

        User user = userService.getUserById(userId);
        ensureSupabaseUser(user);

        Instant now = clock.instant();
        String token =
                Jwts.builder()
                        .setSubject(user.getId().toString())
                        .setAudience(AUTHENTICATED)
                        .claim("role", AUTHENTICATED)
                        .claim("email", user.getEmail())
                        .setIssuedAt(Date.from(now))
                        .setExpiration(Date.from(now.plus(tokenTtl)))
                        .signWith(signingKey(), SignatureAlgorithm.HS256)
                        .compact();
        return new SupabaseTokenResponse(token, tokenTtl.getSeconds());
    }

    private void ensureSupabaseUser(User user) {
        UUID id = user.getId();
        if (provisionedUsers.contains(id)) {
            return;
        }
        if (!supabaseAdminClient.userExists(id)) {
            if (!StringUtils.hasText(user.getEmail())) {
                throw new BusinessException(
                        StatusCode.CONFLICT,
                        "H-Space data needs an account with an email address.");
            }
            supabaseAdminClient.createUser(id, user.getEmail());
        }
        provisionedUsers.add(id);
    }

    // Supabase uses the legacy JWT secret as raw UTF-8 bytes, not Base64.
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
