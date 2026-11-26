package com.henheang.financeapi.payload.transaction;

// Luyra's { affected }: how many of the caller's rows actually changed.
// Another user's ids, or ids already gone, simply don't count.
public record BulkTransactionResponse(int affected) {}
