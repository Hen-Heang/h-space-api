package com.henheang.hubapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * hub-api's config is its own application.yml plus security-api.yml and security-api-{profile}.yml,
 * imported (one copy of the auth settings for both apps). Pins that composition: the shared base
 * and its profile file both load, and hub's own keys still apply. Loads the environment the way
 * Spring Boot does at start-up, without a context or a database.
 */
class ConfigCompositionTest {

    private static StandardEnvironment load(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        ConfigDataEnvironmentPostProcessor.applyTo(
                environment, new DefaultResourceLoader(), new DefaultBootstrapContext(), profiles);
        return environment;
    }

    @Test
    void importsTheSharedSecurityApiSettings() {
        StandardEnvironment env = load("test");

        // security-api.yml
        assertThat(env.getProperty("jwt.refresh-token.max-active-per-user")).isEqualTo("10");
        assertThat(env.getProperty("app.account-lock.max-failed-attempts")).isEqualTo("5");
        // security-api-test.yml: the import's profile-specific file loads too
        // (not spring.datasource.url: a DB_URL environment variable overrides it)
        assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("5");
        assertThat(env.getProperty("app.signup.mode")).isEqualTo("open");
    }

    @Test
    void keepsHubsOwnSettings() {
        StandardEnvironment env = load("test");

        assertThat(env.getProperty("spring.application.name")).isEqualTo("hub-api");
        assertThat(env.getProperty("server.port")).isEqualTo("8084");
        assertThat(env.getProperty("hub.vault.step-up-required")).isEqualTo("false");
        // application-test.yml
        assertThat(env.getProperty("spring.datasource.hikari.schema")).isEqualTo("hub_api_test");
        assertThat(env.getProperty("spring.flyway.default-schema")).isEqualTo("hub_api_test");
    }

    @Test
    void prodTurnsApiDocsOffAndRequiresTls() {
        StandardEnvironment env = load("prod");

        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(env.getProperty("spring.datasource.hikari.data-source-properties.sslmode"))
                .isEqualTo("require");
    }
}
