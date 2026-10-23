package com.henheang.openapi.service;

import com.henheang.openapi.payload.TodoListRequest;
import jakarta.validation.Valid;

public interface TodoService {

    void createTodoList(@Valid TodoListRequest request);
}
