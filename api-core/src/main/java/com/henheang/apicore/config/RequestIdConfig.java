package com.henheang.apicore.config;

import com.henheang.apicore.components.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RequestIdFilter} for every API. hub-api, finance-api and open-api pick this up
 * by scanning {@code com.henheang.apicore}; security-api doesn't scan it and imports it explicitly.
 */
@Configuration(proxyBeanMethods = false)
public class RequestIdConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(new RequestIdFilter());
        registration.addUrlPatterns("/*");
        // First in the servlet chain - ahead of Spring Security - so every log line of the
        // request, including authentication failures, carries the id.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
