package com.henheang.hspaceapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Scans security-api too, so this app validates the same JWTs (filter chain,
// revoked tokens, central GlobalExceptionHandler) against the same database.
@SpringBootApplication
@EntityScan(basePackages = {"com.henheang.hspaceapi.domain", "com.henheang.securityapi.domain"})
@ComponentScan(
        basePackages = {
            "com.henheang.hspaceapi",
            "com.henheang.securityapi",
            "com.henheang.apicore"
        })
@EnableJpaRepositories(
        basePackages = {"com.henheang.hspaceapi.repository", "com.henheang.securityapi.repository"})
public class HspaceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HspaceApiApplication.class, args);
    }
}
