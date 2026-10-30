package com.henheang.hspaceapi.payload.inbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Partial update. Each Optional field has three states, which Jackson's Jdk8
// module gives us for free: absent from the JSON -> null (leave unchanged),
// explicit JSON null -> Optional.empty() (clear), a value -> Optional.of(v).
// Constraints sit on the type argument so they check the wrapped value;
// an explicit null for a required field (content, itemType, tags, status) is
// rejected by InboxItemServiceImpl.
@Getter
@Setter
@NoArgsConstructor
public class InboxItemPatchRequest {

    private Optional<@Size(max = 200, message = "Title must be at most 200 characters") String>
            title;

    private Optional<
                    @NotBlank(message = "Content can't be empty")
                    @Size(max = 4000, message = "Content must be at most 4000 characters") String>
            content;

    private Optional<
                    @Pattern(
                            regexp = InboxItemValues.ITEM_TYPES,
                            message = InboxItemValues.ITEM_TYPE_MESSAGE)
                    String>
            itemType;

    private Optional<
                    @Size(max = 100, message = "At most 100 tags") List<
                            @NotBlank
                            @Size(max = 100, message = "Tags must be at most 100 characters")
                            String>>
            tags;

    private Optional<Instant> eventAt;

    private Optional<UUID> goalId;

    private Boolean pinned;

    private Optional<
                    @Pattern(
                            regexp = InboxItemValues.STATUSES,
                            message = InboxItemValues.STATUS_MESSAGE)
                    String>
            status;

    private Optional<
                    @Pattern(
                            regexp = InboxItemValues.CONVERSION_TARGETS,
                            message = InboxItemValues.CONVERSION_TARGET_MESSAGE)
                    String>
            convertedToType;

    private Optional<UUID> convertedToId;
}
