package com.henheang.engineeringapi.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A project as the API returns it. The owner id is never echoed: it's always the caller. */
public record ProjectResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String githubRepo,
        String productionUrl,
        String hosting,
        String neonProjectId,
        Integer monthlyCostCents,
        String costCurrency,
        BigDecimal computeBudgetCuHours,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
