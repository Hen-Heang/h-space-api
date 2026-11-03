package com.henheang.securityapi;

import com.henheang.apicore.config.RequestIdConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
// security-api doesn't component-scan com.henheang.apicore, unlike the downstream APIs.
@Import(RequestIdConfig.class)
public class SecurityApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityApiApplication.class, args);
    }
}
