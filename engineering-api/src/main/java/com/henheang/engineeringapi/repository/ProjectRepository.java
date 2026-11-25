package com.henheang.engineeringapi.repository;

import com.henheang.engineeringapi.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method is scoped by the owner's {@code userId}; nothing here can see another owner's rows.
 * Enforced by UserScopedRepositoryArchTest - add a method without a used {@code userId} and the
 * build fails.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p WHERE p.userId = :userId ORDER BY lower(p.name), p.slug")
    List<Project> listForUser(@Param("userId") UUID userId);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndSlug(UUID userId, String slug);

    boolean existsByUserIdAndSlugAndIdNot(UUID userId, String slug, UUID id);

    long countByUserId(UUID userId);
}
