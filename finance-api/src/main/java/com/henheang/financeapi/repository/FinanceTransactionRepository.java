package com.henheang.financeapi.repository;

import com.henheang.financeapi.domain.FinanceTransaction;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Every finder is scoped by user_id; there is no unscoped finder. The list
// query lives in FinanceTransactionSearchImpl.
public interface FinanceTransactionRepository
        extends JpaRepository<FinanceTransaction, UUID>, FinanceTransactionSearch {

    Optional<FinanceTransaction> findByIdAndUserId(UUID id, UUID userId);

    // Bulk recategorize: the caller's rows among ids, locked until the
    // transaction ends so a concurrent edit can't change a row's type between
    // the category-type check and the update. Other users' ids just don't
    // match.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM FinanceTransaction t WHERE t.userId = :userId AND t.id IN :ids")
    List<FinanceTransaction> lockAllForUser(
            @Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    // Bulk delete; returns how many of the caller's rows went.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FinanceTransaction t WHERE t.userId = :userId AND t.id IN :ids")
    int deleteAllForUser(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);
}
