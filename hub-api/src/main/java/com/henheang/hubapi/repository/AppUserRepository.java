package com.henheang.hubapi.repository;

import com.henheang.hubapi.domain.AppUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Makes sure the caller's own anchor row exists, so their inbox items satisfy the foreign key.
     * Idempotent and race-safe (two first requests at once both succeed). Called only with the
     * authenticated caller's id, after every auth check has passed.
     */
    @Modifying
    @Query(
            value = "INSERT INTO app_users (id) VALUES (:userId) ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int ensure(@Param("userId") UUID userId);
}
