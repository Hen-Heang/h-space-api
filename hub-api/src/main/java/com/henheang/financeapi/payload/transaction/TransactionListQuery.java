package com.henheang.financeapi.payload.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Query parameters of GET /api/finance/transactions - Luyra's transactionFiltersSchema. Bound as
 * strings and checked here, so a malformed value is a 400 with a field message rather than a type
 * conversion error; FinanceTransactionServiceImpl parses them.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransactionListQuery {

    @NotBlank(message = "start is required")
    @Pattern(regexp = TransactionValues.DATE, message = TransactionValues.DATE_MESSAGE)
    private String start;

    // Exclusive.
    @NotBlank(message = "end is required")
    @Pattern(regexp = TransactionValues.DATE, message = TransactionValues.DATE_MESSAGE)
    private String end;

    @Pattern(regexp = TransactionValues.TYPES, message = TransactionValues.TYPE_MESSAGE)
    private String type;

    @Pattern(regexp = TransactionValues.UUID, message = TransactionValues.UUID_MESSAGE)
    private String categoryId;

    @Pattern(regexp = TransactionValues.UUID, message = TransactionValues.UUID_MESSAGE)
    private String paymentMethodId;

    @Pattern(regexp = TransactionValues.AMOUNT, message = TransactionValues.AMOUNT_MESSAGE)
    private String amountMin;

    @Pattern(regexp = TransactionValues.AMOUNT, message = TransactionValues.AMOUNT_MESSAGE)
    private String amountMax;

    // Trimmed, then at most 200 characters; blank means no search.
    private String search;

    @Pattern(regexp = TransactionValues.SORTS, message = TransactionValues.SORT_MESSAGE)
    private String sort;

    @Pattern(regexp = TransactionValues.PAGE, message = TransactionValues.PAGE_MESSAGE)
    private String page;
}
