package com.henheang.financeapi.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * finance-api's tables live in the same database as security-api's but have their own migrations
 * ({@code db/finance-migration}) and history table, so neither app's Flyway ever sees the other's
 * versions as unknown or out of order. Same model as hub-api's {@code HubFlywayConfig}.
 *
 * <p>The auto-configured Flyway (security-api's {@code db/migration}) runs first only when {@code
 * finance.flyway.run-security-migrations} is true - locally and in tests, where finance-api is
 * alone on the database. In prod security-api migrates its own schema and must be up first.
 */
@Configuration
public class FinanceFlywayConfig {

    static final String FINANCE_LOCATION = "classpath:db/finance-migration";
    static final String FINANCE_HISTORY_TABLE = "flyway_finance_schema_history";

    @Bean
    public FlywayMigrationStrategy financeFlywayMigrationStrategy(
            @Value("${finance.flyway.run-security-migrations:true}")
                    boolean runSecurityMigrations) {
        return flyway -> {
            if (runSecurityMigrations) {
                flyway.migrate();
            }
            Flyway.configure()
                    .configuration(flyway.getConfiguration())
                    .locations(FINANCE_LOCATION)
                    .table(FINANCE_HISTORY_TABLE)
                    // The schema already holds security-api's tables; start
                    // finance's history at 0 so V1 onwards still apply.
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
        };
    }
}
