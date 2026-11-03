package com.henheang.financeapi.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * How many of a user's rows still point at one of their categories or payment methods. Deleting a
 * referenced one answers 409 with this description, as Luyra does (finance-lookup-service.ts
 * describeUsage). Budgets and templates join the count when their tables land (V3, V4).
 */
public record LookupUsage(long transactions, long recurring) {

    public long total() {
        return transactions + recurring;
    }

    /** "12 transactions, 1 recurring rule" - only the non-zero parts, Luyra's wording. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (transactions > 0) {
            parts.add(plural(transactions, "transaction"));
        }
        if (recurring > 0) {
            parts.add(plural(recurring, "recurring rule"));
        }
        return String.join(", ", parts);
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
