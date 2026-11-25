package com.henheang.apicore.archtest.fixture.repository;

import com.henheang.apicore.archtest.fixture.FixtureRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/** Fixture for UserScopedRepositoryRulesTest: only correctly scoped methods. Never a bean. */
@NoRepositoryBean
public interface ScopedFixtureRepository extends JpaRepository<FixtureRow, UUID> {

    Optional<FixtureRow> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT u FROM FixtureRow u WHERE u.id = :userId")
    List<FixtureRow> listForUser(@Param("userId") UUID userId);
}
