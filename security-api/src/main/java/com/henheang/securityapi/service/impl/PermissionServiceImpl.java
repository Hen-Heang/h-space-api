package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.Permission;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import com.henheang.securityapi.payload.PermissionRequest;
import com.henheang.securityapi.repository.PermissionRepository;
import com.henheang.securityapi.service.PermissionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    @Override
    public Permission getOrCreatePermission(String resource, String action, String description) {
        String name = resource + ":" + action;
        return permissionRepository
                .findByName(name)
                .orElseGet(
                        () -> {
                            Permission permission = new Permission();
                            permission.setName(name);
                            permission.setResource(resource);
                            permission.setAction(action);
                            permission.setDescription(description);
                            return permissionRepository.save(permission);
                        });
    }

    @Override
    public Permission createPermission(PermissionRequest request) {
        String name = request.getResource() + ":" + request.getAction();
        if (permissionRepository.findByName(name).isPresent()) {
            throw new BadRequestException("Permission '" + name + "' already exists");
        }
        Permission permission = new Permission();
        permission.setName(name);
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        permission.setDescription(request.getDescription());
        return permissionRepository.save(permission);
    }

    @Override
    public Permission getPermissionById(UUID id) {
        return permissionRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
    }

    @Override
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", allEntries = true)
    public void deletePermission(UUID id) {
        permissionRepository.delete(getPermissionById(id));
    }
}
