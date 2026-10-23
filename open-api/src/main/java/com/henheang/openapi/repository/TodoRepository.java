package com.henheang.openapi.repository;

import com.henheang.openapi.domain.TodoList;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<TodoList, Long> {}
