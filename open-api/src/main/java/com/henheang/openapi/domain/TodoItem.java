package com.henheang.openapi.domain;

import com.henheang.openapi.enums.Priority;
import com.henheang.openapi.enums.Status;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "todo_item")
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", nullable = false, unique = true)
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", referencedColumnName = "list_id")
    private TodoList todoList;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "reminder_date", nullable = true)
    private LocalDateTime reminderDate;

    private Integer position = 0;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @Column(name = "complete_date")
    private LocalDateTime completedDate;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate;

    @OneToMany(mappedBy = "todoItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TodoAttachment> todoAttachments = new ArrayList<>();

    @OneToMany(mappedBy = "todoItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TodoComment> todoComments = new ArrayList<>();

    @OneToMany(mappedBy = "todoItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TodoHistory> todoHistories = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdDate = LocalDateTime.now();
        this.updatedDate = LocalDateTime.now();
        if (this.isCompleted && this.completedDate == null) {
            this.completedDate = LocalDateTime.now();
        } else if (!this.isCompleted) {
            this.completedDate = null;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedDate = LocalDateTime.now();
        if (this.isCompleted && this.completedDate == null) {
            this.completedDate = LocalDateTime.now();
        } else if (!this.isCompleted) {
            this.completedDate = null;
        }
    }
}
