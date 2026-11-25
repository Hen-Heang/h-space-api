package com.henheang.engineeringapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A project the owner runs, in the registry (V2__projects.sql). Owned by {@link #userId} - the
 * owner's Supabase user id - and only ever loaded through a query scoped by it.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    // Assigned by the service before the first save; with a null version,
    // Spring Data still treats the entity as new.
    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "github_repo", length = 140)
    private String githubRepo;

    @Column(name = "production_url", length = 300)
    private String productionUrl;

    @Column(name = "hosting", nullable = false, length = 20)
    private String hosting;

    @Column(name = "neon_project_id", length = 64)
    private String neonProjectId;

    @Column(name = "monthly_cost_cents")
    private Integer monthlyCostCents;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency;

    @Column(name = "compute_budget_cu_hours", precision = 7, scale = 2)
    private BigDecimal computeBudgetCuHours;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
