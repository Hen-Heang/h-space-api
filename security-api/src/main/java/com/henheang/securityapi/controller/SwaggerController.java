package com.henheang.securityapi.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Only while Swagger UI is on (off in prod) - otherwise /api-docs would
// redirect to a page that doesn't exist.
@Controller
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", matchIfMissing = true)
public class SwaggerController {

    @GetMapping("/api-docs")
    public String apiDocs() {
        return "redirect:/swagger-ui/index.html";
    }
}
