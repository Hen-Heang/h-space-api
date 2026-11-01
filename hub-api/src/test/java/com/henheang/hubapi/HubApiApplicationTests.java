package com.henheang.hubapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class HubApiApplicationTests {

    @Autowired private JdbcTemplate jdbcTemplate;

    // Context start-up already proves the hub migration applied and that
    // ddl-auto=validate accepts the entities. This pins the separate history
    // table, so hub versions never mix with security-api's.
    @Test
    void hubMigrationsHaveTheirOwnHistory() {
        Integer vaultMigrations =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM flyway_hub_schema_history"
                                + " WHERE script = 'V1__vault.sql' AND success",
                        Integer.class);
        Integer leakedIntoSecurityHistory =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE script = 'V1__vault.sql'",
                        Integer.class);
        assertThat(vaultMigrations).isEqualTo(1);
        assertThat(leakedIntoSecurityHistory).isZero();
    }
}
