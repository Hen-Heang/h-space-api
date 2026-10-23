package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.LoginHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {
    Page<LoginHistory> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
