package com.henheang.financeapi.repository;

import com.henheang.financeapi.domain.FinanceCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Every finder is scoped by user_id - the only tenant boundary, since the
// source tables had no RLS (docs/finance/DATABASE-SCHEMA.md §7). There is no
// unscoped finder; don't add one.
public interface FinanceCategoryRepository extends JpaRepository<FinanceCategory, UUID> {

    // Same order as Luyra (order by name asc); id breaks ties so the order is
    // stable.
    List<FinanceCategory> findAllByUserIdOrderByNameAscIdAsc(UUID userId);

    Optional<FinanceCategory> findByIdAndUserId(UUID id, UUID userId);

    // Names/icons/colors for a page of transactions. Scoped by user_id, unlike
    // Luyra's join, which matched on id alone.
    List<FinanceCategory> findAllByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    // lower() on both sides, matching the uq_finance_categories_user_name
    // index and Luyra's findCategoryByName. At most one row exists per name
    // (the unique index), so the list has 0 or 1 entries.
    @Query(
            "SELECT c FROM FinanceCategory c WHERE c.userId = :userId"
                    + " AND lower(c.name) = lower(:name)")
    List<FinanceCategory> findByUserIdAndNameIgnoringCase(
            @Param("userId") UUID userId, @Param("name") String name);
}
