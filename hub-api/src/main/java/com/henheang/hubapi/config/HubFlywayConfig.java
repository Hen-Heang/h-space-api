package com.henheang.hubapi.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrates the whole database at start-up, in a fixed order: security-api's schema first (the
 * auto-configured Flyway, {@code db/migration}, history {@code flyway_schema_history}), then hub's
 * ({@code db/hub-migration}), then finance's ({@code db/finance-migration}). Hub and finance tables
 * reference {@code users}, so auth must migrate first.
 *
 * <p>Each set keeps its own history table, so their version numbers never collide or look out of
 * order to one another. Before 2026-11-26 security-api, hub-api and finance-api were separate apps
 * and the product apps skipped security's migrations in prod; this app is now the only one that
 * deploys, so it always runs all three.
 */
@Configuration
public class HubFlywayConfig {

    static final String HUB_LOCATION = "classpath:db/hub-migration";
    static final String HUB_HISTORY_TABLE = "flyway_hub_schema_history";
    static final String FINANCE_LOCATION = "classpath:db/finance-migration";
    static final String FINANCE_HISTORY_TABLE = "flyway_finance_schema_history";

    @Bean
    public FlywayMigrationStrategy hubFlywayMigrationStrategy() {
        return flyway -> {
            flyway.migrate();
            migrate(flyway, HUB_LOCATION, HUB_HISTORY_TABLE);
            migrate(flyway, FINANCE_LOCATION, FINANCE_HISTORY_TABLE);
        };
    }

    private static void migrate(Flyway base, String location, String historyTable) {
        Flyway.configure()
                .configuration(base.getConfiguration())
                .locations(location)
                .table(historyTable)
                // The schema already holds security-api's tables; start this
                // set's history at 0 so its V1 onwards still apply.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
