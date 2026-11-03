package com.henheang.securityapi.archtest.fixture.repository;

import com.henheang.securityapi.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/** Fixture for UserScopedRepositoryRulesTest: only correctly scoped methods. Never a bean. */
@NoRepositoryBean
public interface ScopedFixtureRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT u FROM User u WHERE u.id = :userId")
    List<User> listForUser(@Param("userId") UUID userId);
}
