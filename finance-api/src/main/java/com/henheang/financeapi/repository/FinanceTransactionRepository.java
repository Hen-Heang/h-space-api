package com.henheang.financeapi.repository;

import com.henheang.financeapi.domain.FinanceTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Every finder is scoped by user_id; there is no unscoped finder. The list
// query lives in FinanceTransactionSearchImpl.
public interface FinanceTransactionRepository
        extends JpaRepository<FinanceTransaction, UUID>, FinanceTransactionSearch {

    Optional<FinanceTransaction> findByIdAndUserId(UUID id, UUID userId);
}
