package com.henheang.financeapi.domain;

import com.henheang.securityapi.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// An income or expense entry (Luyra finance_transactions). Money is BigDecimal
// end to end; amountKrw is canonical and, for a USD entry, computed by
// KrwConverter - never taken from the client. See V2__finance_transactions.sql.
@Entity
@Table(name = "finance_transactions")
@Getter
@Setter
@NoArgsConstructor
public class FinanceTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // Calendar date (no time zone), as entered.
    @Column(name = "date", nullable = false)
    private LocalDate date;

    // income | expense
    @Column(name = "type", nullable = false, length = 10)
    private String type;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    // KRW | USD
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_krw", nullable = false, precision = 14, scale = 0)
    private BigDecimal amountKrw;

    // The original USD amount; null for KRW entries.
    @Column(name = "amount_usd", precision = 12, scale = 2)
    private BigDecimal amountUsd;

    // USD -> KRW rate used for amountKrw; null for KRW entries.
    @Column(name = "exchange_rate", precision = 14, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    // Set only by the recurring job (Phase 5); the CRUD API never changes it.
    @Column(name = "recurring_id", updatable = false)
    private UUID recurringId;

    @Column(name = "note", length = 500)
    private String note;
}
