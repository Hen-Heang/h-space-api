package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.MfaRecoveryCode;
import com.henheang.securityapi.domain.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    List<MfaRecoveryCode> findByUserAndUsedFalse(User user);

    void deleteByUser(User user);

    long countByUserAndUsedFalse(User user);
}
