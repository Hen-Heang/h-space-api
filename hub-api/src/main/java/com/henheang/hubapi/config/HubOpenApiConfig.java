package com.henheang.hubapi.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HubOpenApiConfig {

    @Bean
    public GroupedOpenApi hubApi() {
        return GroupedOpenApi.builder().group("hub").pathsToMatch("/api/hub/**").build();
    }
}
