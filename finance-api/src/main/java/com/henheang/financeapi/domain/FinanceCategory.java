package com.henheang.financeapi.domain;

import com.henheang.securityapi.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A user's transaction category (Luyra finance_categories). The owner is set
// once from the JWT and never changes; see V1__finance_lookups.sql.
@Entity
@Table(name = "finance_categories")
@Getter
@Setter
@NoArgsConstructor
public class FinanceCategory extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "icon", length = 8)
    private String icon;

    @Column(name = "color", length = 7)
    private String color;

    // income | expense | both
    @Column(name = "type", nullable = false, length = 10)
    private String type;

    // essential | commitment | growth | flexible | avoidable, or null
    @Column(name = "spending_class", length = 20)
    private String spendingClass;
}
