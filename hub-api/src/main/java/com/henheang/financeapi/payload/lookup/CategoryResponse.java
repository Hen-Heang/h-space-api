package com.henheang.financeapi.payload.lookup;

import com.henheang.financeapi.domain.FinanceCategory;
import java.util.UUID;

// Field names and nullability match Luyra's Category type (types/finance.ts),
// so the frontend needs no row mapping. user_id is never returned.
public record CategoryResponse(
        UUID id, String name, String icon, String color, String type, String spendingClass) {

    public static CategoryResponse from(FinanceCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getIcon(),
                category.getColor(),
                category.getType(),
                category.getSpendingClass());
    }
}
