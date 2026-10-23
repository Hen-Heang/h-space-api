package com.henheang.securityapi.config;

import com.henheang.securityapi.domain.Permission;
import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.service.PermissionService;
import com.henheang.securityapi.service.RoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleService roleService;
    private final PermissionService permissionService;

    @Override
    public void run(String... args) {
        Role userRole = roleService.getOrCreateRole("ROLE_USER");
        Role adminRole = roleService.getOrCreateRole("ROLE_ADMIN");

        // Baseline resource:action grants - extend the permission set via the
        // /api/admin/roles and /api/admin/permissions endpoints as the app
        // grows rather than adding more of these.
        Permission userRead =
                permissionService.getOrCreatePermission("user", "read", "Read own user profile");
        Permission userUpdate =
                permissionService.getOrCreatePermission(
                        "user", "update", "Update own user profile");
        Permission userDelete =
                permissionService.getOrCreatePermission(
                        "user", "delete", "Delete own user account");
        Permission userManage =
                permissionService.getOrCreatePermission(
                        "user", "manage", "Manage all user accounts");
        Permission roleManage =
                permissionService.getOrCreatePermission(
                        "role", "manage", "Manage roles and role-permission grants");
        Permission permissionManage =
                permissionService.getOrCreatePermission(
                        "permission", "manage", "Manage permissions");
        Permission auditRead =
                permissionService.getOrCreatePermission("audit", "read", "Read audit logs");

        for (Permission permission : List.of(userRead, userUpdate, userDelete)) {
            userRole = grantIfMissing(userRole, permission);
        }
        for (Permission permission :
                List.of(
                        userRead,
                        userUpdate,
                        userDelete,
                        userManage,
                        roleManage,
                        permissionManage,
                        auditRead)) {
            adminRole = grantIfMissing(adminRole, permission);
        }
    }

    // getOrCreateRole/getOrCreatePermission are idempotent, but grantPermission
    // isn't checked for an existing grant - this guard is what makes re-running
    // this initializer on every app restart a no-op after the first run.
    //
    // Returns the (possibly merged) role so callers chain the updated version:
    // grantPermission bumps the row's @Version on save, and reusing the stale
    // in-memory role across multiple grants in the same loop throws
    // StaleObjectStateException on the second grant.
    private Role grantIfMissing(Role role, Permission permission) {
        boolean alreadyGranted =
                role.getPermissions().stream()
                        .anyMatch(granted -> granted.getId().equals(permission.getId()));
        if (alreadyGranted) {
            return role;
        }
        return roleService.grantPermission(role, permission);
    }
}
