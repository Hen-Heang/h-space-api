package com.henheang.securityapi.controller;

import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.payload.RoleResponse;
import com.henheang.securityapi.security.UserPrincipal;
import com.henheang.securityapi.service.RoleService;
import com.henheang.securityapi.service.UserRoleService;
import com.henheang.securityapi.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users/{userId}/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserRoleController extends BaseController {

    private final UserService userService;
    private final RoleService roleService;
    private final UserRoleService userRoleService;

    @GetMapping
    public Object getRoles(@PathVariable UUID userId) {
        User user = userService.getUserById(userId);
        return ok(userRoleService.getRolesForUser(user).stream().map(RoleResponse::from).toList());
    }

    @PostMapping("/{roleId}")
    public Object assignRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getUserById(userId);
        Role role = roleService.getRoleById(roleId);
        userRoleService.assignRole(user, role, principal.getId());
        return ok();
    }

    @DeleteMapping("/{roleId}")
    public Object revokeRole(@PathVariable UUID userId, @PathVariable UUID roleId) {
        User user = userService.getUserById(userId);
        Role role = roleService.getRoleById(roleId);
        userRoleService.revokeRole(user, role);
        return ok();
    }
}
