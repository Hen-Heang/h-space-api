package com.henheang.hspaceapi.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HspaceOpenApiConfig {

    @Bean
    public GroupedOpenApi hspaceApi() {
        return GroupedOpenApi.builder().group("hspace").pathsToMatch("/api/v1/**").build();
    }
}
