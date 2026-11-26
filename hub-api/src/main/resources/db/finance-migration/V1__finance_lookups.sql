-- Finance categories and payment methods: the first slice of Luyra finance
-- moving into finance-api (docs/finance/IMPLEMENTATION-PLAN.md, Phase 1a).
--
-- finance-api migration history (flyway_finance_schema_history; see
-- FinanceFlywayConfig), not security-api's: the tables live in the same
-- database and reference users(id), but their version numbers never mix.
--
-- Same columns as Luyra's Neon tables (luyra-web db/migrations/004_finance.sql)
-- so rows copy across with their ids, plus what Luyra only enforced in code
-- (docs/finance/DATABASE-SCHEMA.md §5). An import must pre-check its rows
-- against these constraints and stop on a violation, never clean them:
--   * one name per user, case-insensitive (Luyra: a pre-query, race-prone);
--   * name 1..40 chars, icon 1..8, color #RRGGBB (Luyra: API validation only);
--   * category type NOT NULL (nullable in Luyra);
--   * UNIQUE (id, user_id), the target of the composite foreign keys that
--     transactions, budgets, templates and recurring rules will use so a row
--     can only point at its own owner's category / payment method;
--   * updated_at and version (BaseEntity, optimistic locking).
-- Ownership is enforced in the services: every query is scoped by user_id
-- taken from the JWT, and another user's id answers 404.

CREATE TABLE finance_categories (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(40) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 40),
    icon           VARCHAR(8) CHECK (icon IS NULL OR char_length(btrim(icon)) BETWEEN 1 AND 8),
    color          VARCHAR(7) CHECK (color IS NULL OR color ~ '^#[0-9a-fA-F]{6}$'),
    type           VARCHAR(10) NOT NULL DEFAULT 'expense'
        CHECK (type IN ('income', 'expense', 'both')),
    spending_class VARCHAR(20)
        CHECK (spending_class IS NULL
            OR spending_class IN ('essential', 'commitment', 'growth', 'flexible', 'avoidable')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_finance_categories_id_user UNIQUE (id, user_id)
);

CREATE UNIQUE INDEX uq_finance_categories_user_name
    ON finance_categories (user_id, lower(name));

CREATE TABLE finance_payment_methods (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(40) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 40),
    icon       VARCHAR(8) CHECK (icon IS NULL OR char_length(btrim(icon)) BETWEEN 1 AND 8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_finance_payment_methods_id_user UNIQUE (id, user_id)
);

CREATE UNIQUE INDEX uq_finance_payment_methods_user_name
    ON finance_payment_methods (user_id, lower(name));
