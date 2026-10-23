package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.repository.PermissionRepository;
import com.henheang.securityapi.service.PermissionQueryService;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionQueryServiceImpl implements PermissionQueryService {

    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "userPermissions", key = "#userId")
    public Set<String> getPermissionNames(UUID userId) {
        return permissionRepository.findPermissionNamesByUserId(userId);
    }
}
