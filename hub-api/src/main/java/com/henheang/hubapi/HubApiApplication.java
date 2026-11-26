package com.henheang.hubapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The one deployable H-Space app: auth (security-api, embedded) plus the product areas - inbox and
 * H-Vault ({@code com.henheang.hubapi}) and finance ({@code com.henheang.financeapi}). Each area
 * keeps its own packages and its own migration history (see HubFlywayConfig).
 */
@SpringBootApplication
@EntityScan(
        basePackages = {
            "com.henheang.hubapi.domain",
            "com.henheang.financeapi.domain",
            "com.henheang.securityapi.domain"
        })
@ComponentScan(
        basePackages = {
            "com.henheang.hubapi",
            "com.henheang.financeapi",
            "com.henheang.securityapi",
            "com.henheang.apicore"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.henheang.hubapi.repository",
            "com.henheang.financeapi.repository",
            "com.henheang.securityapi.repository"
        })
public class HubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubApiApplication.class, args);
    }
}
