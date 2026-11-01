package com.henheang.hubapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.henheang.hubapi.domain", "com.henheang.securityapi.domain"})
@ComponentScan(
        basePackages = {"com.henheang.hubapi", "com.henheang.securityapi", "com.henheang.apicore"})
@EnableJpaRepositories(
        basePackages = {"com.henheang.hubapi.repository", "com.henheang.securityapi.repository"})
public class HubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubApiApplication.class, args);
    }
}
