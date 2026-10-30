package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    // Emails are stored as typed; "continue with email" matches them
    // case-insensitively so a differently-cased address finds the same account.
    Optional<User> findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(String email);

    // Usernames are stored lowercase (UsernameRules.normalize) - pass a
    // normalized value.
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByName(String name);

    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.phoneNumber = :identifier")
    Optional<User> findByEmailOrPhoneNumber(@Param("identifier") String identifier);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    // Records a TOTP time step as used, in one statement so two requests with
    // the same code can't both win. 0 means it, or a later one, already was.
    @Modifying
    @Transactional
    @Query(
            "UPDATE User u SET u.mfaLastUsedStep = :step WHERE u.id = :id"
                    + " AND (u.mfaLastUsedStep IS NULL OR u.mfaLastUsedStep < :step)")
    int claimMfaTimeStep(@Param("id") UUID id, @Param("step") long step);
}
