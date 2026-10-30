package com.henheang.hspaceapi.service;

import com.henheang.hspaceapi.payload.inbox.InboxItemPatchRequest;
import com.henheang.hspaceapi.payload.inbox.InboxItemRequest;
import com.henheang.hspaceapi.payload.inbox.InboxItemResponse;
import java.util.List;
import java.util.UUID;

// Every method takes the caller's user id from the JWT; an item that belongs
// to someone else is reported as not found (404), never as forbidden.
public interface InboxItemService {

    List<InboxItemResponse> list(
            UUID userId, String status, String itemType, String query, Integer limit);

    InboxItemResponse get(UUID userId, UUID id);

    InboxItemResponse create(UUID userId, InboxItemRequest request);

    InboxItemResponse update(UUID userId, UUID id, InboxItemPatchRequest request);

    void delete(UUID userId, UUID id);
}
