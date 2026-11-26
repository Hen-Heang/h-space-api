-- Transactions and recurring rules (docs/finance/IMPLEMENTATION-PLAN.md,
-- Phase 1b). Recurring rules get their table now because transactions
-- reference it; their endpoints and job come in Phase 5.
--
-- Same columns as Luyra's Neon tables (luyra-web db/migrations/004 + 014) so
-- rows copy across with their ids, plus what Luyra enforced only in code or
-- not at all (docs/finance/DATABASE-SCHEMA.md §3.1, §3.7, §5). An import must
-- pre-check its rows against these and stop on a violation:
--   * composite foreign keys (category_id, user_id) etc.: a row can only point
--     at its own owner's category, payment method or recurring rule - Luyra's
--     FKs checked existence only, so another user's id was accepted;
--   * whole-won amount_krw > 0 (NUMERIC(14,0)); USD original amount with at
--     most 2 decimals, rate with at most 6 (decision D-7);
--   * currency is KRW or USD, and USD rows carry their original amount and
--     rate while KRW rows carry neither;
--   * description NOT NULL (nullable in Luyra);
--   * updated_at / version on both tables (BaseEntity, optimistic locking).
--
-- ON DELETE SET NULL (recurring_id) nulls only that column of the composite
-- key (PostgreSQL 15+): deleting a rule keeps the transactions it created,
-- as Luyra's 014 does.

CREATE TABLE finance_recurring_transactions (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type              VARCHAR(10) NOT NULL CHECK (type IN ('income', 'expense')),
    description       VARCHAR(200) NOT NULL
        CHECK (char_length(btrim(description)) BETWEEN 1 AND 200),
    currency          VARCHAR(3) NOT NULL DEFAULT 'KRW' CHECK (currency IN ('KRW', 'USD')),
    amount_krw        NUMERIC(14, 0) NOT NULL CHECK (amount_krw > 0),
    amount_usd        NUMERIC(12, 2) CHECK (amount_usd IS NULL OR amount_usd > 0),
    exchange_rate     NUMERIC(14, 6) CHECK (exchange_rate IS NULL OR exchange_rate > 0),
    category_id       UUID,
    payment_method_id UUID,
    note              VARCHAR(500),
    frequency         VARCHAR(10) NOT NULL
        CHECK (frequency IN ('daily', 'weekly', 'monthly', 'yearly')),
    next_date         DATE NOT NULL,
    last_applied      DATE,
    anchor_day        SMALLINT NOT NULL CHECK (anchor_day BETWEEN 1 AND 31),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_finance_recurring_id_user UNIQUE (id, user_id),
    -- A USD rule keeps its original amount; the rate is today's estimate.
    CONSTRAINT ck_finance_recurring_currency_shape
        CHECK ((currency = 'USD') = (amount_usd IS NOT NULL)),
    CONSTRAINT fk_finance_recurring_category FOREIGN KEY (category_id, user_id)
        REFERENCES finance_categories (id, user_id),
    CONSTRAINT fk_finance_recurring_payment_method FOREIGN KEY (payment_method_id, user_id)
        REFERENCES finance_payment_methods (id, user_id)
);

CREATE INDEX idx_finance_recurring_user ON finance_recurring_transactions (user_id);
CREATE INDEX idx_finance_recurring_due
    ON finance_recurring_transactions (next_date) WHERE is_active;
CREATE INDEX idx_finance_recurring_user_category
    ON finance_recurring_transactions (user_id, category_id) WHERE category_id IS NOT NULL;
CREATE INDEX idx_finance_recurring_user_payment_method
    ON finance_recurring_transactions (user_id, payment_method_id)
    WHERE payment_method_id IS NOT NULL;

CREATE TABLE finance_transactions (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date              DATE NOT NULL,
    type              VARCHAR(10) NOT NULL CHECK (type IN ('income', 'expense')),
    description       VARCHAR(200) NOT NULL
        CHECK (char_length(btrim(description)) BETWEEN 1 AND 200),
    currency          VARCHAR(3) NOT NULL DEFAULT 'KRW' CHECK (currency IN ('KRW', 'USD')),
    -- Canonical amount for every total. For USD it is the server-computed
    -- round(amount_usd * exchange_rate, HALF_UP).
    amount_krw        NUMERIC(14, 0) NOT NULL CHECK (amount_krw > 0),
    -- The original USD amount (Luyra's column name kept for the copy).
    amount_usd        NUMERIC(12, 2) CHECK (amount_usd IS NULL OR amount_usd > 0),
    exchange_rate     NUMERIC(14, 6) CHECK (exchange_rate IS NULL OR exchange_rate > 0),
    category_id       UUID,
    payment_method_id UUID,
    recurring_id      UUID,
    note              VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_finance_transactions_currency_shape CHECK (
        (currency = 'USD' AND amount_usd IS NOT NULL AND exchange_rate IS NOT NULL)
        OR (currency = 'KRW' AND amount_usd IS NULL AND exchange_rate IS NULL)),
    CONSTRAINT fk_finance_transactions_category FOREIGN KEY (category_id, user_id)
        REFERENCES finance_categories (id, user_id),
    CONSTRAINT fk_finance_transactions_payment_method FOREIGN KEY (payment_method_id, user_id)
        REFERENCES finance_payment_methods (id, user_id),
    CONSTRAINT fk_finance_transactions_recurring FOREIGN KEY (recurring_id, user_id)
        REFERENCES finance_recurring_transactions (id, user_id)
        ON DELETE SET NULL (recurring_id)
);

CREATE INDEX idx_finance_transactions_user_date
    ON finance_transactions (user_id, date DESC, created_at DESC);
CREATE INDEX idx_finance_transactions_user_category
    ON finance_transactions (user_id, category_id) WHERE category_id IS NOT NULL;
CREATE INDEX idx_finance_transactions_user_payment_method
    ON finance_transactions (user_id, payment_method_id) WHERE payment_method_id IS NOT NULL;
-- One generated occurrence per rule per day: the recurring job's
-- idempotency key (Phase 5), same as Luyra's 014.
CREATE UNIQUE INDEX uq_finance_transactions_recurring_occurrence
    ON finance_transactions (recurring_id, date) WHERE recurring_id IS NOT NULL;
