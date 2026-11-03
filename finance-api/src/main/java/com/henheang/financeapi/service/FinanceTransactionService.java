package com.henheang.financeapi.service;

import com.henheang.financeapi.payload.transaction.TransactionListQuery;
import com.henheang.financeapi.payload.transaction.TransactionPageResponse;
import com.henheang.financeapi.payload.transaction.TransactionRequest;
import com.henheang.financeapi.payload.transaction.TransactionResponse;
import java.util.UUID;

// userId is always the caller's id from the JWT principal.
public interface FinanceTransactionService {

    TransactionPageResponse list(UUID userId, TransactionListQuery query);

    TransactionResponse create(UUID userId, TransactionRequest request);

    /** Full replace, like Luyra's PATCH (D-15). */
    TransactionResponse update(UUID userId, UUID id, TransactionRequest request);

    void delete(UUID userId, UUID id);
}
