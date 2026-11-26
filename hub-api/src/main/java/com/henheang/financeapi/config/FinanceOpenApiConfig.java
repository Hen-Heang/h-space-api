package com.henheang.financeapi.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinanceOpenApiConfig {

    @Bean
    public GroupedOpenApi financeApi() {
        return GroupedOpenApi.builder().group("finance").pathsToMatch("/api/finance/**").build();
    }
}
