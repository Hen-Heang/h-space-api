package com.henheang.engineeringapi;

import com.henheang.apicore.config.RequestIdConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/**
 * The Engineering Hub API (docs/engineering-api.md): owner-only, depends on api-core only. It scans
 * just its own package - never {@code com.henheang.securityapi}, whose login system, users tables
 * and scheduled jobs it must not inherit - and imports the request-id filter from api-core.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(RequestIdConfig.class)
public class EngineeringApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineeringApiApplication.class, args);
    }
}
