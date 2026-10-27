package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.EmailLoginCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLoginCodeRepository extends JpaRepository<EmailLoginCode, UUID> {
    Optional<EmailLoginCode> findByEmail(String email);
}
