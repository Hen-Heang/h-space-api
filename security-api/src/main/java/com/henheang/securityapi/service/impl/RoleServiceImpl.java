package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.Permission;
import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import com.henheang.securityapi.payload.RoleRequest;
import com.henheang.securityapi.repository.RoleRepository;
import com.henheang.securityapi.service.PermissionService;
import com.henheang.securityapi.service.RoleService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;

    @Override
    public Role getOrCreateRole(String roleName) {
        return roleRepository
                .findByName(roleName)
                .orElseGet(
                        () -> {
                            Role newRole = new Role();
                            newRole.setName(roleName);
                            return roleRepository.save(newRole);
                        });
    }

    @Override
    public Role createRole(RoleRequest request) {
        if (roleRepository.findByName(request.getName()).isPresent()) {
            throw new BadRequestException("Role '" + request.getName() + "' already exists");
        }
        Role role = new Role();
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        return roleRepository.save(role);
    }

    @Override
    public Role updateRole(UUID id, RoleRequest request) {
        Role role = getRoleById(id);
        if (request.getName() != null) {
            role.setName(request.getName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        return roleRepository.save(role);
    }

    @Override
    public Role getRoleById(UUID id) {
        return roleRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", allEntries = true)
    public void deleteRole(UUID id) {
        roleRepository.delete(getRoleById(id));
    }

    // A role's permission set is shared by every user holding that role, and
    // there's no per-user cache key to target individually here - clearing
    // the whole "userPermissions" cache on this rare, admin-driven change is
    // simpler than tracking which users are affected.
    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", allEntries = true)
    public Role grantPermission(UUID roleId, UUID permissionId) {
        Role role = getRoleById(roleId);
        Permission permission = permissionService.getPermissionById(permissionId);
        return grantPermission(role, permission);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", allEntries = true)
    public Role grantPermission(Role role, Permission permission) {
        role.getPermissions().add(permission);
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", allEntries = true)
    public Role revokePermission(UUID roleId, UUID permissionId) {
        Role role = getRoleById(roleId);
        Permission permission = permissionService.getPermissionById(permissionId);
        role.getPermissions().remove(permission);
        return roleRepository.save(role);
    }
}
