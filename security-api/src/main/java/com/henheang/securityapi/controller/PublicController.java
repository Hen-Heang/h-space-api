package com.henheang.securityapi.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController extends BaseController {

    @GetMapping("/ping")
    public Object ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "API is running");
        response.put("timestamp", System.currentTimeMillis());

        return ok(response);
    }
}
