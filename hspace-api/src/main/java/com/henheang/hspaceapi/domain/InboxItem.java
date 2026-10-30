package com.henheang.hspaceapi.domain;

import com.henheang.securityapi.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One Quick Capture item. The table's generated `search` tsvector isn't
// mapped - it's only read through InboxItemRepository's full-text query.
@Entity
@Table(name = "inbox_items")
@Getter
@Setter
@NoArgsConstructor
public class InboxItem extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "item_type", nullable = false, columnDefinition = "text")
    private String itemType;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "event_at")
    private Instant eventAt;

    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "source", nullable = false, columnDefinition = "text")
    private String source;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "converted_to_type", columnDefinition = "text")
    private String convertedToType;

    @Column(name = "converted_to_id")
    private UUID convertedToId;
}
