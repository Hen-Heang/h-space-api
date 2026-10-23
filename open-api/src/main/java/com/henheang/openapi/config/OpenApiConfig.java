package com.henheang.openapi.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("todoOpenApiConfig")
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi todoApi() {
        return GroupedOpenApi.builder().group("todo").pathsToMatch("/api/todo/**").build();
    }
}
