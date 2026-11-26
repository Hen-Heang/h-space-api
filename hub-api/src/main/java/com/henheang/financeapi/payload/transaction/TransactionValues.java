package com.henheang.financeapi.payload.transaction;

// Allowed values and limits for transactions, kept in step with the CHECK
// constraints in V2__finance_transactions.sql and with Luyra's
// lib/validation/finance.ts (createTransactionSchema, transactionFiltersSchema).
public final class TransactionValues {

    public static final String TYPES = "^(income|expense)$";
    public static final String TYPE_MESSAGE = "Type must be one of income, expense";

    public static final String CURRENCIES = "^(KRW|USD)$";
    public static final String CURRENCY_MESSAGE = "Currency must be one of KRW, USD";

    public static final String SORTS = "^(date_desc|date_asc|amount_desc|amount_asc)$";
    public static final String SORT_MESSAGE =
            "Sort must be one of date_desc, date_asc, amount_desc, amount_asc";

    public static final String DATE = "^\\d{4}-\\d{2}-\\d{2}$";
    public static final String DATE_MESSAGE = "Expected a date like 2026-11-03";

    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    public static final String UUID_MESSAGE = "Expected a UUID";

    // Non-negative number, as zod's z.coerce.number().min(0) accepts.
    public static final String AMOUNT = "^\\d{1,12}(\\.\\d+)?$";
    public static final String AMOUNT_MESSAGE = "Expected a non-negative amount";

    public static final String PAGE = "^\\d{1,6}$";
    public static final String PAGE_MESSAGE = "Page must be a whole number from 0";

    public static final int DESCRIPTION_MAX = 200;
    public static final int NOTE_MAX = 500;
    public static final int SEARCH_MAX = 200;

    /** Luyra's PAGE_SIZE; the list fetches one more row to compute hasMore. */
    public static final int PAGE_SIZE = 20;

    private TransactionValues() {}

    static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
