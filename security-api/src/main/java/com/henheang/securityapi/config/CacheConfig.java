package com.henheang.securityapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Backs the "userPermissions" cache used by PermissionQueryService /
// CustomPermissionEvaluator, so per-request @PreAuthorize hasPermission(...)
// checks don't hit the DB on every call. TTL (rather than no expiration)
// bounds how long a revoked role/permission can still grant access before
// RoleServiceImpl/UserRoleServiceImpl's @CacheEvict takes effect - both
// eviction and expiry are needed since eviction only covers grants/revokes
// made through this service, not out-of-band DB changes.
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager(
            @Value("${cache.permission.ttl-minutes:5}") long ttlMinutes,
            @Value("${cache.permission.max-size:10000}") long maxSize) {
        CaffeineCacheManager manager = new CaffeineCacheManager("userPermissions");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                        .maximumSize(maxSize));
        return manager;
    }
}
