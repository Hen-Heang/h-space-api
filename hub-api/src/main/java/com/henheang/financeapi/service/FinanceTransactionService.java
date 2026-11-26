package com.henheang.financeapi.service;

import com.henheang.financeapi.payload.transaction.BulkTransactionRequest;
import com.henheang.financeapi.payload.transaction.BulkTransactionResponse;
import com.henheang.financeapi.payload.transaction.DescriptionSuggestionResponse;
import com.henheang.financeapi.payload.transaction.TransactionListQuery;
import com.henheang.financeapi.payload.transaction.TransactionPageResponse;
import com.henheang.financeapi.payload.transaction.TransactionRequest;
import com.henheang.financeapi.payload.transaction.TransactionResponse;
import java.util.List;
import java.util.UUID;

// userId is always the caller's id from the JWT principal.
public interface FinanceTransactionService {

    TransactionPageResponse list(UUID userId, TransactionListQuery query);

    TransactionResponse create(UUID userId, TransactionRequest request);

    /** Full replace, like Luyra's PATCH (D-15). */
    TransactionResponse update(UUID userId, UUID id, TransactionRequest request);

    void delete(UUID userId, UUID id);

    /** Bulk delete or recategorize of up to 200 of the caller's transactions. */
    BulkTransactionResponse bulk(UUID userId, BulkTransactionRequest request);

    /** Description autocomplete over the last 365 days (Seoul), at most 6. */
    List<DescriptionSuggestionResponse> suggest(UUID userId, String query, String type);
}
