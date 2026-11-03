package com.henheang.financeapi.payload.transaction;

import com.henheang.financeapi.domain.FinanceCategory;
import com.henheang.financeapi.domain.FinancePaymentMethod;
import com.henheang.financeapi.domain.FinanceTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Field names and nullability match Luyra's Transaction type (types/finance.ts). Amounts are JSON
 * numbers, never strings, with trailing zeros dropped (12.30 -> 12.3, 1388.500000 -> 1388.5) as a
 * JavaScript Number would print them. user_id and recurring_id are never returned (Luyra doesn't
 * return recurring_id either).
 */
public record TransactionResponse(
        UUID id,
        LocalDate date,
        String type,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        String description,
        BigDecimal amountKrw,
        String currency,
        BigDecimal originalAmount,
        BigDecimal exchangeRate,
        UUID paymentMethodId,
        String paymentMethodName,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    /** category / paymentMethod are the caller's own rows for the ids, or null. */
    public static TransactionResponse from(
            FinanceTransaction transaction,
            FinanceCategory category,
            FinancePaymentMethod paymentMethod) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getDate(),
                transaction.getType(),
                transaction.getCategoryId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getIcon(),
                category == null ? null : category.getColor(),
                transaction.getDescription(),
                plain(transaction.getAmountKrw()),
                transaction.getCurrency(),
                plain(transaction.getAmountUsd()),
                plain(transaction.getExchangeRate()),
                transaction.getPaymentMethodId(),
                paymentMethod == null ? null : paymentMethod.getName(),
                transaction.getNote(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }

    // Drops trailing zeros without going to a negative scale, which Jackson
    // would print in exponent form (1E+3).
    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }
}
