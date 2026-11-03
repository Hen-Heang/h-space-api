package com.henheang.securityapi.archtest.fixture.repository;

import com.henheang.securityapi.domain.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/** Fixture for UserScopedRepositoryRulesTest: each method breaks rule 2 in a different way. */
@NoRepositoryBean
public interface UnscopedFixtureRepository extends JpaRepository<User, UUID> {

    // No userId at all.
    List<User> findAllByEmail(String email);

    // Takes userId, but the query never uses it.
    @Query("SELECT u FROM User u WHERE u.email = :email")
    List<User> byEmailIgnoringOwner(@Param("email") String email, @Param("userId") UUID userId);

    // Takes userId, but the derived query does not filter on it.
    List<User> findAllByName(String name, UUID userId);
}
