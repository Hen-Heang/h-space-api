package com.henheang.securityapi.service;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

// Backs CustomPermissionEvaluator's hasPermission(...) checks. Kept separate
// from User.getPermissions() (used to build JWT-filter authorities) because
// this one is cached (see CacheConfig's "userPermissions" cache) - it runs on
// every @PreAuthorize hasPermission(...) evaluation, not just once per
// request's authentication.
@Service
public interface PermissionQueryService {

    Set<String> getPermissionNames(UUID userId);
}
