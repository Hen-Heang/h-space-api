package com.henheang.securityapi.security;

import com.henheang.securityapi.service.PermissionQueryService;
import java.io.Serializable;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

// Backs @PreAuthorize("hasPermission(#id, 'User', 'read')")-style checks.
// Resolves to the same "{resource}:{action}" strings as Permission.name (see
// domain/Permission.java) so the same DB-driven grants back both
// hasAuthority(...) (set at JWT-filter time, see User.getAuthorities()) and
// hasPermission(...) (evaluated per-call via the cached PermissionQueryService).
// Authorization here is target-type-based rather than per-instance (targetId
// is unused) - "can this user read Users" rather than "can this user read
// *this* User" - ownership checks (e.g. #id == authentication.principal.id)
// are expressed separately in the @PreAuthorize expression itself.
@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final PermissionQueryService permissionQueryService;

    @Override
    public boolean hasPermission(
            Authentication authentication, Object targetDomainObject, Object permission) {
        if (targetDomainObject == null) {
            return false;
        }
        return hasPermission(
                authentication, null, targetDomainObject.getClass().getSimpleName(), permission);
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        String required =
                targetType.toLowerCase(Locale.ROOT)
                        + ":"
                        + permission.toString().toLowerCase(Locale.ROOT);
        return permissionQueryService.getPermissionNames(principal.getId()).contains(required);
    }
}
