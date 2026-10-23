package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.domain.User;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public interface UserRoleService {

    // assignedBy is null for system-assigned defaults (e.g. ROLE_USER at
    // signup); non-null when an admin grants a role explicitly.
    void assignRole(User user, Role role, UUID assignedBy);

    default void assignDefaultRole(User user, Role role) {
        assignRole(user, role, null);
    }

    void revokeRole(User user, Role role);

    List<Role> getRolesForUser(User user);
}
