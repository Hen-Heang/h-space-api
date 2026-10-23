package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.Permission;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(String name);

    @Query(
            "SELECT DISTINCT p.name FROM UserRole ur JOIN ur.role r JOIN r.permissions p "
                    + "WHERE ur.user.id = :userId")
    Set<String> findPermissionNamesByUserId(@Param("userId") UUID userId);
}
