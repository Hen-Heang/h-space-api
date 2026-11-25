package com.henheang.engineeringapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Startup must fail closed: no issuer or no owner means no app, never an open or empty gate. */
class EngineeringAuthPropertiesTest {

    private static final String OWNER = "0b0e5c2a-7d3c-4c8e-9a51-2f6d8e4b1a01";

    @Configuration
    @EnableConfigurationProperties(EngineeringAuthProperties.class)
    static class Props {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(Props.class);

    @Test
    void binds_andDerivesTheKeysUrl_fromTheIssuer() {
        runner.withPropertyValues(
                        "engineering.auth.issuer=https://example-ref.supabase.invalid/auth/v1/",
                        "engineering.auth.owner-ids=" + OWNER)
                .run(
                        context -> {
                            EngineeringAuthProperties auth =
                                    context.getBean(EngineeringAuthProperties.class);
                            assertThat(auth.normalizedIssuer())
                                    .isEqualTo("https://example-ref.supabase.invalid/auth/v1");
                            assertThat(auth.jwksUri())
                                    .isEqualTo(
                                            "https://example-ref.supabase.invalid/auth/v1/.well-known/jwks.json");
                            assertThat(auth.audience()).isEqualTo("authenticated");
                            assertThat(auth.ownerIds()).containsExactly(UUID.fromString(OWNER));
                        });
    }

    @Test
    void refusesToStart_withoutAnOwner() {
        runner.withPropertyValues(
                        "engineering.auth.issuer=https://example-ref.supabase.invalid/auth/v1",
                        "engineering.auth.owner-ids=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStart_withoutAnIssuer() {
        runner.withPropertyValues("engineering.auth.owner-ids=" + OWNER)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStart_withAnOwnerIdThatIsNotAUuid() {
        runner.withPropertyValues(
                        "engineering.auth.issuer=https://example-ref.supabase.invalid/auth/v1",
                        "engineering.auth.owner-ids=owner@example.invalid")
                .run(context -> assertThat(context).hasFailed());
    }
}
