package com.henheang.financeapi.payload.lookup;

// Allowed values and limits for categories and payment methods, kept in step
// with the CHECK constraints in V1__finance_lookups.sql and with Luyra's
// lib/validation/finance.ts (createCategorySchema, createPaymentMethodSchema).
public final class LookupValues {

    public static final int NAME_MAX = 40;
    public static final int ICON_MAX = 8;

    public static final String CATEGORY_TYPES = "^(income|expense|both)$";
    public static final String CATEGORY_TYPE_MESSAGE = "Type must be one of income, expense, both";
    public static final String DEFAULT_CATEGORY_TYPE = "expense";

    public static final String SPENDING_CLASSES =
            "^(essential|commitment|growth|flexible|avoidable)$";
    public static final String SPENDING_CLASS_MESSAGE =
            "Spending class must be one of essential, commitment, growth, flexible, avoidable";

    public static final String HEX_COLOR = "^#[0-9a-fA-F]{6}$";
    public static final String HEX_COLOR_MESSAGE = "Color must be a hex value like #10b981";

    public static final String NAME_REQUIRED = "Name is required";
    public static final String NAME_TOO_LONG = "Name must be at most 40 characters";
    public static final String ICON_SIZE = "Icon must be 1 to 8 characters";

    private LookupValues() {}

    // Luyra's zod schemas trim before validating; requests do the same in
    // their setters so " Food " is stored as "Food" and length limits apply
    // to the trimmed value.
    static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
