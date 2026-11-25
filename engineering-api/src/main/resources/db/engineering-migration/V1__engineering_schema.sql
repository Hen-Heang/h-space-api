-- engineering-api's schema. Flyway creates it (spring.flyway.schemas) and
-- records this module's history in flyway_engineering_schema_history inside
-- it. Tables arrive with the features that need them (project registry: S2).
COMMENT ON SCHEMA ${flyway:defaultSchema} IS
    'engineering-api (Engineering Hub): owner-only infrastructure metadata. No personal data.';
