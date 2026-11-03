package com.henheang.financeapi.domain;

import com.henheang.securityapi.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A user's payment-method label (Luyra finance_payment_methods).
@Entity
@Table(name = "finance_payment_methods")
@Getter
@Setter
@NoArgsConstructor
public class FinancePaymentMethod extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "icon", length = 8)
    private String icon;
}
