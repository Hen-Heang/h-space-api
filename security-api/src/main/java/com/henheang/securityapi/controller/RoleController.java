package com.henheang.securityapi.controller;

import com.henheang.securityapi.payload.RoleRequest;
import com.henheang.securityapi.payload.RoleResponse;
import com.henheang.securityapi.service.RoleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RoleController extends BaseController {

    private final RoleService roleService;

    @GetMapping
    public Object getAllRoles() {
        return ok(roleService.getAllRoles().stream().map(RoleResponse::from).toList());
    }

    @GetMapping("/{id}")
    public Object getRole(@PathVariable UUID id) {
        return ok(RoleResponse.from(roleService.getRoleById(id)));
    }

    @PostMapping
    public Object createRole(@Valid @RequestBody RoleRequest request) {
        return ok(RoleResponse.from(roleService.createRole(request)));
    }

    @PatchMapping("/{id}")
    public Object updateRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return ok(RoleResponse.from(roleService.updateRole(id, request)));
    }

    @DeleteMapping("/{id}")
    public Object deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ok();
    }

    @PostMapping("/{roleId}/permissions/{permissionId}")
    public Object grantPermission(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        return ok(RoleResponse.from(roleService.grantPermission(roleId, permissionId)));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public Object revokePermission(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        return ok(RoleResponse.from(roleService.revokePermission(roleId, permissionId)));
    }
}
