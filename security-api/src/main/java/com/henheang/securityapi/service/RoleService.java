package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.Permission;
import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.payload.RoleRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public interface RoleService {

    Role getOrCreateRole(String roleName);

    Role createRole(RoleRequest request);

    Role updateRole(UUID id, RoleRequest request);

    Role getRoleById(UUID id);

    List<Role> getAllRoles();

    void deleteRole(UUID id);

    Role grantPermission(UUID roleId, UUID permissionId);

    // For callers (e.g. DataInitializer) that already hold both entities and
    // would otherwise re-fetch each by id just to call the overload above.
    Role grantPermission(Role role, Permission permission);

    Role revokePermission(UUID roleId, UUID permissionId);
}
