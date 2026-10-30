package com.henheang.hspaceapi.payload.inbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Create request. Limits mirror the inbox_items CHECK constraints so a bad
// value is a 400 with a field message, not a database error.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InboxItemRequest {

    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Content is required")
    @Size(max = 4000, message = "Content must be at most 4000 characters")
    private String content;

    @NotBlank(message = "Item type is required")
    @Pattern(regexp = InboxItemValues.ITEM_TYPES, message = InboxItemValues.ITEM_TYPE_MESSAGE)
    private String itemType;

    @Size(max = 100, message = "At most 100 tags")
    private List<@NotBlank @Size(max = 100, message = "Tags must be at most 100 characters") String>
            tags;

    private Instant eventAt;

    private UUID goalId;

    @Pattern(regexp = InboxItemValues.SOURCES, message = InboxItemValues.SOURCE_MESSAGE)
    private String source;
}
