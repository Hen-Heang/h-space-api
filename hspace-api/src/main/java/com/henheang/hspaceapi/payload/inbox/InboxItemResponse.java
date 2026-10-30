package com.henheang.hspaceapi.payload.inbox;

import com.henheang.hspaceapi.domain.InboxItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Field names match the web app's InboxItem type (apps/web/lib/inbox.ts), so
// its Spring adapter needs no row mapping. user_id is never returned.
public record InboxItemResponse(
        UUID id,
        String title,
        String content,
        String itemType,
        List<String> tags,
        Instant capturedAt,
        Instant eventAt,
        UUID goalId,
        String status,
        String source,
        boolean pinned,
        String convertedToType,
        UUID convertedToId,
        Instant createdAt,
        Instant updatedAt) {

    public static InboxItemResponse from(InboxItem item) {
        return new InboxItemResponse(
                item.getId(),
                item.getTitle(),
                item.getContent(),
                item.getItemType(),
                item.getTags() == null ? List.of() : List.copyOf(item.getTags()),
                item.getCapturedAt(),
                item.getEventAt(),
                item.getGoalId(),
                item.getStatus(),
                item.getSource(),
                item.isPinned(),
                item.getConvertedToType(),
                item.getConvertedToId(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
