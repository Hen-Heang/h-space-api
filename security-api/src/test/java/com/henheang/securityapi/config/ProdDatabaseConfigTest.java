package com.henheang.securityapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * The prod profile's connection pool is sized for a small VPS and an external managed database, and
 * always asks for TLS. The prod profile can't boot in tests (no datasource defaults), so this reads
 * application-prod.yml directly - like OpenApiDocsDisabledIntegrationTest.
 */
class ProdDatabaseConfigTest {

    @Test
    void poolIsSmallAndTlsIsRequiredByDefault() throws IOException {
        PropertySourcesPropertyResolver prod = prodResolver(Map.of());

        assertThat(prod.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                .isEqualTo(5);
        assertThat(prod.getProperty("spring.datasource.hikari.minimum-idle", Integer.class))
                .isEqualTo(1);
        assertThat(prod.getProperty("spring.datasource.hikari.data-source-properties.sslmode"))
                .isEqualTo("require");
    }

    @Test
    void poolSizeComesFromEnvironmentWhenSet() throws IOException {
        PropertySourcesPropertyResolver prod =
                prodResolver(Map.of("DB_POOL_MAX_SIZE", "3", "DB_POOL_MIN_IDLE", "0"));

        assertThat(prod.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                .isEqualTo(3);
        assertThat(prod.getProperty("spring.datasource.hikari.minimum-idle", Integer.class))
                .isEqualTo(0);
    }

    private static PropertySourcesPropertyResolver prodResolver(Map<String, Object> env)
            throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("env", env));
        new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"))
                .forEach(sources::addLast);
        return new PropertySourcesPropertyResolver(sources);
    }
}
