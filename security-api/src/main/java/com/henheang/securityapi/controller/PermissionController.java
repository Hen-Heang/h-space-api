package com.henheang.securityapi.controller;

import com.henheang.securityapi.payload.PermissionRequest;
import com.henheang.securityapi.payload.PermissionResponse;
import com.henheang.securityapi.service.PermissionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController extends BaseController {

    private final PermissionService permissionService;

    @GetMapping
    public Object getAllPermissions() {
        return ok(
                permissionService.getAllPermissions().stream()
                        .map(PermissionResponse::from)
                        .toList());
    }

    @GetMapping("/{id}")
    public Object getPermission(@PathVariable UUID id) {
        return ok(PermissionResponse.from(permissionService.getPermissionById(id)));
    }

    @PostMapping
    public Object createPermission(@Valid @RequestBody PermissionRequest request) {
        return ok(PermissionResponse.from(permissionService.createPermission(request)));
    }

    @DeleteMapping("/{id}")
    public Object deletePermission(@PathVariable UUID id) {
        permissionService.deletePermission(id);
        return ok();
    }
}
