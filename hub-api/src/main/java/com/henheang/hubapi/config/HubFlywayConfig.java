package com.henheang.hubapi.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * hub-api's tables live in the same database as security-api's but have their own migrations
 * ({@code db/hub-migration}) and history table, so neither app's Flyway ever sees the other's
 * versions as unknown or out of order.
 *
 * <p>The auto-configured Flyway (security-api's {@code db/migration}) runs first only when {@code
 * hub.flyway.run-security-migrations} is true - locally and in tests, where hub-api is alone on the
 * database. In prod security-api migrates its own schema and must be up first.
 */
@Configuration
public class HubFlywayConfig {

    static final String HUB_LOCATION = "classpath:db/hub-migration";
    static final String HUB_HISTORY_TABLE = "flyway_hub_schema_history";

    @Bean
    public FlywayMigrationStrategy hubFlywayMigrationStrategy(
            @Value("${hub.flyway.run-security-migrations:true}") boolean runSecurityMigrations) {
        return flyway -> {
            if (runSecurityMigrations) {
                flyway.migrate();
            }
            Flyway.configure()
                    .configuration(flyway.getConfiguration())
                    .locations(HUB_LOCATION)
                    .table(HUB_HISTORY_TABLE)
                    // The schema already holds security-api's tables; start
                    // hub's history at 0 so V1 onwards still apply.
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
        };
    }
}
