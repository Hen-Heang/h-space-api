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
}
