package com.henheang.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.henheang.openapi.domain", "com.henheang.securityapi.domain"})
@ComponentScan(
        basePackages = {
            "com.henheang.openapi",
            "com.henheang.securityapi",
            "com.henheang.commonapi"
        })
@EnableJpaRepositories(
        basePackages = {"com.henheang.openapi.repository", "com.henheang.securityapi.repository"})
public class OpenApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenApiApplication.class, args);
    }
}
