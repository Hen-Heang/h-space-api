package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.SignupInvite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupInviteRepository extends JpaRepository<SignupInvite, UUID> {

    // Emails are stored lowercase - pass a normalized value.
    Optional<SignupInvite> findFirstByEmailAndUsedAtIsNull(String email);

    List<SignupInvite> findAllByOrderByCreatedAtDesc();
}
