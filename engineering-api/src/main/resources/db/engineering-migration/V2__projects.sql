-- Project registry (docs/engineering-api.md, S2). One row per project the
-- owner runs. Infrastructure metadata only: names, a GitHub repo, a public
-- URL, a Neon project id, costs. No secrets, no personal data.
--
-- user_id is the owner's Supabase user id (the access token's sub). There's
-- no users table here, so no foreign key; every query is scoped by it
-- (UserScopedRepositoryRules).
CREATE TABLE projects (
    id                      UUID         PRIMARY KEY,
    user_id                 UUID         NOT NULL,
    name                    VARCHAR(80)  NOT NULL CHECK (length(btrim(name)) > 0),
    slug                    VARCHAR(60)  NOT NULL CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    description             VARCHAR(500),
    -- "owner/name" on GitHub.
    github_repo             VARCHAR(140) CHECK (github_repo ~ '^[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}$'),
    production_url          VARCHAR(300) CHECK (production_url ~ '^https://\S+$'),
    hosting                 VARCHAR(20)  NOT NULL
        CHECK (hosting IN ('vercel', 'railway', 'neon', 'supabase', 'github', 'other')),
    neon_project_id         VARCHAR(64)  CHECK (neon_project_id ~ '^[a-z0-9-]{1,64}$'),
    -- Entered by hand; cost import is out of scope.
    monthly_cost_cents      INTEGER      CHECK (monthly_cost_cents BETWEEN 0 AND 100000000),
    cost_currency           VARCHAR(3)   CHECK (cost_currency ~ '^[A-Z]{3}$'),
    -- Neon compute budget per billing period, for the usage guard (S4).
    compute_budget_cu_hours NUMERIC(7, 2) CHECK (compute_budget_cu_hours > 0),
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_projects_user_slug UNIQUE (user_id, slug),
    -- A cost needs its currency and vice versa.
    CONSTRAINT chk_projects_cost_currency
        CHECK ((monthly_cost_cents IS NULL) = (cost_currency IS NULL))
);

CREATE INDEX idx_projects_user_name ON projects (user_id, lower(name));
