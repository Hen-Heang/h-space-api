package com.henheang.financeapi.payload.transaction;

import java.util.List;

// GET /api/finance/transactions body: Luyra's { transactions, hasMore }. No
// total count, like Luyra.
public record TransactionPageResponse(List<TransactionResponse> transactions, boolean hasMore) {}
