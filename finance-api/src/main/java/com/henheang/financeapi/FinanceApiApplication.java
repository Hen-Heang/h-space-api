package com.henheang.financeapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.henheang.financeapi.domain", "com.henheang.securityapi.domain"})
@ComponentScan(
        basePackages = {
            "com.henheang.financeapi",
            "com.henheang.securityapi",
            "com.henheang.apicore"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.henheang.financeapi.repository",
            "com.henheang.securityapi.repository"
        })
public class FinanceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApiApplication.class, args);
    }
}
