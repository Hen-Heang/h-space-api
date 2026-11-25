package com.henheang.engineeringapi.controller;

import com.henheang.apicore.components.common.api.ApiResponse;
import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.engineeringapi.payload.ProjectRequest;
import com.henheang.engineeringapi.payload.ProjectResponse;
import com.henheang.engineeringapi.security.CurrentOwner;
import com.henheang.engineeringapi.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The project registry (docs/engineering-api.md, S2). Owner-only like everything under {@code
 * /api/engineering}; the owner comes from the token, and another owner's project answers 404.
 */
@RestController
@RequestMapping("/api/engineering/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projects;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ok(projects.list(CurrentOwner.id(jwt)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(projects.get(CurrentOwner.id(jwt), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequest request) {
        return ok(projects.create(CurrentOwner.id(jwt), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectResponse> replace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request) {
        return ok(projects.replace(CurrentOwner.id(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        projects.delete(CurrentOwner.id(jwt), id);
        return ok(null);
    }

    private static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().status(StatusCode.SUCCESS).data(data).build();
    }
}
