package com.henheang.openapi.controller;

import com.henheang.openapi.payload.TodoListRequest;
import com.henheang.openapi.service.TodoService;
import com.henheang.securityapi.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todo/v1")
@RequiredArgsConstructor
@Tag(name = "Todo List", description = "Endpoints for managing todo lists")
@SecurityRequirement(name = "bearerAuth")
public class TodoController extends BaseController {

    private final TodoService todoService;

    @PostMapping("/create")
    @Operation(
            summary = "Create a todo list",
            description = "Creates a new todo list for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Todo list created successfully")
    public Object createTodoList(@Valid @RequestBody TodoListRequest request) {
        todoService.createTodoList(request);
        return ok();
    }
}
