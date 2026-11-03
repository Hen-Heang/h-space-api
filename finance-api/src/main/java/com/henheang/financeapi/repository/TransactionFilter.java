package com.henheang.financeapi.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A validated transaction-list query. start is inclusive and end exclusive (half-open, like Luyra).
 * The optional fields are null when absent; amounts compare against amount_krw, inclusive.
 */
public record TransactionFilter(
        LocalDate start,
        LocalDate end,
        String type,
        UUID categoryId,
        UUID paymentMethodId,
        BigDecimal amountMin,
        BigDecimal amountMax,
        String search,
        TransactionSort sort) {}
