package com.henheang.engineeringapi.payload;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create or replace a project. An allowlist: unknown JSON fields are rejected
 * (spring.jackson.deserialization.fail-on-unknown-properties), and the owner never comes from the
 * body. The same rules as the table's CHECK constraints (V2__projects.sql), so a valid request
 * never meets a database error.
 *
 * @param version required on update (optimistic locking: the version it was read at); must be
 *     absent on create
 */
public record ProjectRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank
                @Size(max = 60)
                @Pattern(
                        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                        message = "lowercase letters, digits and single hyphens only")
                String slug,
        @Size(max = 500) String description,
        @Size(max = 140)
                @Pattern(
                        regexp = "^[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}$",
                        message = "must be owner/name on GitHub")
                String githubRepo,
        @Size(max = 300) @Pattern(regexp = "^https://\\S+$", message = "must be an https:// URL")
                String productionUrl,
        @NotBlank
                @Pattern(
                        regexp = "^(vercel|railway|neon|supabase|github|other)$",
                        message = "must be one of vercel, railway, neon, supabase, github, other")
                String hosting,
        @Size(max = 64)
                @Pattern(
                        regexp = "^[a-z0-9-]{1,64}$",
                        message = "lowercase letters, digits and hyphens only")
                String neonProjectId,
        @PositiveOrZero @Max(100_000_000) Integer monthlyCostCents,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code like USD")
                String costCurrency,
        @DecimalMin(value = "0.01")
                @DecimalMax(value = "99999.99")
                @Digits(integer = 5, fraction = 2)
                BigDecimal computeBudgetCuHours,
        @PositiveOrZero Long version) {}
