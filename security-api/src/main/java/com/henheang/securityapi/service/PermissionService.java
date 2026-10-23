package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.Permission;
import com.henheang.securityapi.payload.PermissionRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public interface PermissionService {

    Permission getOrCreatePermission(String resource, String action, String description);

    Permission createPermission(PermissionRequest request);

    Permission getPermissionById(UUID id);

    List<Permission> getAllPermissions();

    void deletePermission(UUID id);
}
