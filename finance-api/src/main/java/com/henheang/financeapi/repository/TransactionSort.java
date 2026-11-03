package com.henheang.financeapi.repository;

import java.util.Arrays;
import java.util.Optional;

// Luyra's transactionSortSchema, with the same primary orders as its
// SORT_CLAUSES. Every order ends in created_at and id so pages are stable
// when dates or amounts tie (Luyra stops at created_at).
public enum TransactionSort {
    DATE_DESC("date_desc"),
    DATE_ASC("date_asc"),
    AMOUNT_DESC("amount_desc"),
    AMOUNT_ASC("amount_asc");

    private final String value;

    TransactionSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<TransactionSort> fromValue(String value) {
        return Arrays.stream(values()).filter(sort -> sort.value.equals(value)).findFirst();
    }
}
