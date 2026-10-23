package com.henheang.openapi.domain;

import com.henheang.openapi.enums.PermissionLevel;
import com.henheang.securityapi.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "list_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ListShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_id")
    private Long shareId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", referencedColumnName = "list_id")
    private TodoList todoList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false)
    private PermissionLevel permissionLevel = PermissionLevel.VIEW;

    @Column(name = "share_date", nullable = false)
    private LocalDateTime shareDate;

    @PrePersist
    protected void onCreate() {
        this.shareDate = LocalDateTime.now();
    }
}
