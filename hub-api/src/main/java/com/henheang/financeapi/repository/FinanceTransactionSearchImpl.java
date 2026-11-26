package com.henheang.financeapi.repository;

import com.henheang.financeapi.domain.FinanceTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// The transaction list as a Criteria query: only the filters actually given
// become predicates, so no nullable parameter ever reaches Postgres untyped.
// user_id is always the first predicate.
class FinanceTransactionSearchImpl implements FinanceTransactionSearch {

    private static final char ESCAPE = '\\';

    @PersistenceContext private EntityManager entityManager;

    @Override
    public List<FinanceTransaction> search(
            UUID userId, TransactionFilter filter, int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<FinanceTransaction> query = cb.createQuery(FinanceTransaction.class);
        Root<FinanceTransaction> t = query.from(FinanceTransaction.class);

        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(t.get("userId"), userId));
        where.add(cb.greaterThanOrEqualTo(t.get("date"), filter.start()));
        where.add(cb.lessThan(t.get("date"), filter.end()));
        if (filter.type() != null) {
            where.add(cb.equal(t.get("type"), filter.type()));
        }
        if (filter.categoryId() != null) {
            where.add(cb.equal(t.get("categoryId"), filter.categoryId()));
        }
        if (filter.paymentMethodId() != null) {
            where.add(cb.equal(t.get("paymentMethodId"), filter.paymentMethodId()));
        }
        if (filter.amountMin() != null) {
            where.add(cb.greaterThanOrEqualTo(t.get("amountKrw"), filter.amountMin()));
        }
        if (filter.amountMax() != null) {
            where.add(cb.lessThanOrEqualTo(t.get("amountKrw"), filter.amountMax()));
        }
        if (filter.search() != null) {
            // Case-insensitive substring like Luyra's ILIKE, but % and _ in
            // the search are literal (Luyra passed them through as wildcards).
            String pattern = "%" + escapeLike(filter.search().toLowerCase(Locale.ROOT)) + "%";
            where.add(cb.like(cb.lower(t.get("description")), pattern, ESCAPE));
        }
        query.select(t)
                .where(where.toArray(Predicate[]::new))
                .orderBy(orders(cb, t, filter.sort()));

        return entityManager
                .createQuery(query)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    private static List<Order> orders(
            CriteriaBuilder cb, Root<FinanceTransaction> t, TransactionSort sort) {
        return switch (sort) {
            case DATE_DESC ->
                    List.of(
                            cb.desc(t.get("date")),
                            cb.desc(t.get("createdAt")),
                            cb.desc(t.get("id")));
            case DATE_ASC ->
                    List.of(cb.asc(t.get("date")), cb.asc(t.get("createdAt")), cb.asc(t.get("id")));
            case AMOUNT_DESC ->
                    List.of(
                            cb.desc(t.get("amountKrw")),
                            cb.desc(t.get("date")),
                            cb.desc(t.get("createdAt")),
                            cb.desc(t.get("id")));
            case AMOUNT_ASC ->
                    List.of(
                            cb.asc(t.get("amountKrw")),
                            cb.desc(t.get("date")),
                            cb.desc(t.get("createdAt")),
                            cb.desc(t.get("id")));
        };
    }

    static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
