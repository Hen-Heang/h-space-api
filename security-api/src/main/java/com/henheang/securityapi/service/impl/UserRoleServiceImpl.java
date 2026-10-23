package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.domain.UserRole;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.repository.UserRoleRepository;
import com.henheang.securityapi.service.UserRoleService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", key = "#user.id")
    public void assignRole(User user, Role role, UUID assignedBy) {
        // Idempotent - re-assigning a role the user already actively holds is
        // a no-op rather than a duplicate grant row.
        if (userRoleRepository.findByUserAndRole(user, role).isPresent()) {
            return;
        }
        userRoleRepository.save(new UserRole(user, role, assignedBy));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", key = "#user.id")
    public void revokeRole(User user, Role role) {
        UserRole userRole =
                userRoleRepository
                        .findByUserAndRole(user, role)
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "User does not have role '"
                                                        + role.getName()
                                                        + "'"));
        userRoleRepository.delete(userRole);
    }

    @Override
    public List<Role> getRolesForUser(User user) {
        return userRoleRepository.findAllByUser(user).stream().map(UserRole::getRole).toList();
    }
}
