package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.domain.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findAllByUser(User user);

    Optional<UserRole> findByUserAndRole(User user, Role role);
}
