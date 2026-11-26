package com.henheang.financeapi.repository;

import com.henheang.financeapi.domain.LookupUsage;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Counts the rows that reference a category or payment method, scoped by the
// owner. The composite foreign keys guarantee every reference has the same
// user_id, so these counts are complete.
@Repository
@RequiredArgsConstructor
public class LookupUsageRepository {

    private static final String CATEGORY_USAGE =
            "SELECT"
                    + " (SELECT count(*) FROM finance_transactions"
                    + "  WHERE user_id = :userId AND category_id = :id) AS transactions,"
                    + " (SELECT count(*) FROM finance_recurring_transactions"
                    + "  WHERE user_id = :userId AND category_id = :id) AS recurring";

    private static final String PAYMENT_METHOD_USAGE =
            "SELECT"
                    + " (SELECT count(*) FROM finance_transactions"
                    + "  WHERE user_id = :userId AND payment_method_id = :id) AS transactions,"
                    + " (SELECT count(*) FROM finance_recurring_transactions"
                    + "  WHERE user_id = :userId AND payment_method_id = :id) AS recurring";

    private final NamedParameterJdbcTemplate jdbc;

    public LookupUsage countCategoryUsage(UUID userId, UUID categoryId) {
        return count(CATEGORY_USAGE, userId, categoryId);
    }

    public LookupUsage countPaymentMethodUsage(UUID userId, UUID paymentMethodId) {
        return count(PAYMENT_METHOD_USAGE, userId, paymentMethodId);
    }

    private LookupUsage count(String sql, UUID userId, UUID id) {
        return jdbc.queryForObject(
                sql,
                Map.of("userId", userId, "id", id),
                (rs, row) -> new LookupUsage(rs.getLong("transactions"), rs.getLong("recurring")));
    }
}
