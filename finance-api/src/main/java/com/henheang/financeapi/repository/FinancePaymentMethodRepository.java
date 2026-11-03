package com.henheang.financeapi.repository;

import com.henheang.financeapi.domain.FinancePaymentMethod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Every finder is scoped by user_id; see FinanceCategoryRepository.
public interface FinancePaymentMethodRepository extends JpaRepository<FinancePaymentMethod, UUID> {

    List<FinancePaymentMethod> findAllByUserIdOrderByNameAscIdAsc(UUID userId);

    Optional<FinancePaymentMethod> findByIdAndUserId(UUID id, UUID userId);

    @Query(
            "SELECT p FROM FinancePaymentMethod p WHERE p.userId = :userId"
                    + " AND lower(p.name) = lower(:name)")
    List<FinancePaymentMethod> findByUserIdAndNameIgnoringCase(
            @Param("userId") UUID userId, @Param("name") String name);
}
