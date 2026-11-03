package com.henheang.financeapi.repository;

import com.henheang.financeapi.domain.FinanceTransaction;
import java.util.List;
import java.util.UUID;

// Custom fragment of FinanceTransactionRepository: the filtered list needs an
// offset/limit pair (page * 20, 21 rows) that Pageable can't express.
public interface FinanceTransactionSearch {

    List<FinanceTransaction> search(UUID userId, TransactionFilter filter, int offset, int limit);
}
