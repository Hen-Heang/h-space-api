package com.henheang.hspaceapi.controller;

import com.henheang.hspaceapi.payload.inbox.InboxItemPatchRequest;
import com.henheang.hspaceapi.payload.inbox.InboxItemRequest;
import com.henheang.hspaceapi.service.InboxItemService;
import com.henheang.securityapi.controller.BaseController;
import com.henheang.securityapi.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Quick Capture Inbox. The owner always comes from the JWT, never the body;
// another user's item answers 404.
@RestController
@RequestMapping("/api/v1/inbox-items")
@RequiredArgsConstructor
@Tag(name = "Inbox", description = "Quick Capture items (moved from Supabase kori_inbox_items)")
public class InboxItemController extends BaseController {

    private final InboxItemService inboxItemService;

    @Operation(
            summary = "List inbox items",
            description =
                    "Newest captured first. status/itemType filter ('all' or omitted = any); q runs"
                            + " an English full-text search over title and content; limit 1-500.")
    @GetMapping
    public Object list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        return ok(inboxItemService.list(principal.getId(), status, itemType, q, limit));
    }

    @GetMapping("/{id}")
    public Object get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ok(inboxItemService.get(principal.getId(), id));
    }

    @PostMapping
    public Object create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InboxItemRequest request) {
        return ok(inboxItemService.create(principal.getId(), request));
    }

    @Operation(
            summary = "Update an inbox item",
            description =
                    "Partial update: omitted fields are unchanged, an explicit null clears a"
                            + " nullable field (title, eventAt, goalId, convertedToType,"
                            + " convertedToId).")
    @PatchMapping("/{id}")
    public Object update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody InboxItemPatchRequest request) {
        return ok(inboxItemService.update(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Object delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        inboxItemService.delete(principal.getId(), id);
        return ok();
    }
}
