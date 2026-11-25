package com.henheang.engineeringapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway ran engineering-api's own migrations, into its own schema, with its own history table - so
 * it can never collide with another module's versions, even on a shared local database.
 */
@SpringBootTest
class EngineeringSchemaIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void v1_isApplied_inItsOwnSchemaAndHistoryTable() {
        Integer applied =
                jdbc.queryForObject(
                        "SELECT count(*) FROM engineering_api_test.flyway_engineering_schema_history"
                                + " WHERE script = 'V1__engineering_schema.sql' AND success",
                        Integer.class);
        assertThat(applied).isEqualTo(1);

        String comment =
                jdbc.queryForObject(
                        "SELECT obj_description(oid, 'pg_namespace') FROM pg_namespace"
                                + " WHERE nspname = 'engineering_api_test'",
                        String.class);
        assertThat(comment).startsWith("engineering-api (Engineering Hub)");
    }

    @Test
    void v2_createsTheProjectsTable_inTheSameSchema() {
        Integer applied =
                jdbc.queryForObject(
                        "SELECT count(*) FROM engineering_api_test.flyway_engineering_schema_history"
                                + " WHERE script = 'V2__projects.sql' AND success",
                        Integer.class);
        assertThat(applied).isEqualTo(1);

        Integer table =
                jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.tables"
                                + " WHERE table_schema = 'engineering_api_test'"
                                + " AND table_name = 'projects'",
                        Integer.class);
        assertThat(table).isEqualTo(1);
    }

    @Test
    void theDatabaseItselfRejectsWhatTheApiWouldRefuse() {
        // Defence in depth: the CHECK constraints hold even if a bug skipped
        // the request validation. Synthetic ids only.
        String insert =
                "INSERT INTO engineering_api_test.projects"
                        + " (id, user_id, name, slug, hosting, monthly_cost_cents, cost_currency,"
                        + " created_at, updated_at)"
                        + " VALUES (gen_random_uuid(), gen_random_uuid(), 'x', ?, 'vercel', ?, ?,"
                        + " now(), now())";
        assertRejected(insert, "Not A Slug", null, null);
        assertRejected(insert, "cost-alone", 500, null);
    }

    private void assertRejected(String sql, Object... args) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(sql, args))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
