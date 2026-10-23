package com.henheang.openapi.service;

import com.henheang.openapi.domain.TodoList;
import com.henheang.openapi.payload.TodoListRequest;
import com.henheang.openapi.payload.TodoListResponse;
import com.henheang.openapi.repository.TodoRepository;
import com.henheang.openapi.util.AuthUtils;
import com.henheang.securityapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void createTodoList(TodoListRequest request) {
        // Get the user by ID
        TodoList todoList = new TodoList();
        todoList.setUser(
                userRepository
                        .findById(AuthUtils.getCurrentUserId())
                        .orElseThrow(() -> new IllegalArgumentException("User not found")));
        todoList.setTitle(request.getTitle());
        todoList.setDescription(request.getDescription());
        todoList.setColor(request.getColor());
        todoList.setPosition(
                request.getPosition() != null ? request.getPosition().toString() : "0");

        TodoList savedTodoList = todoRepository.save(todoList);
        mapToResponse(savedTodoList);
    }

    private void mapToResponse(TodoList savedTodoList) {
        TodoListResponse.builder()
                .listId(String.valueOf(savedTodoList.getListId()))
                .title(savedTodoList.getTitle())
                .description(savedTodoList.getDescription())
                .color(savedTodoList.getColor())
                .position(savedTodoList.getPosition())
                .isArchived(savedTodoList.getIsArchived())
                .createdAt(savedTodoList.getCreatedAt())
                .updatedAt(savedTodoList.getUpdatedAt())
                .itemCount(
                        savedTodoList.getTodoItems() != null
                                ? savedTodoList.getTodoItems().size()
                                : 0)
                .build();
    }
}
