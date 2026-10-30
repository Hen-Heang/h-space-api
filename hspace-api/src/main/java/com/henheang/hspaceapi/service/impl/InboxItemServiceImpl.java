package com.henheang.hspaceapi.service.impl;

import com.henheang.hspaceapi.domain.InboxItem;
import com.henheang.hspaceapi.payload.inbox.InboxItemPatchRequest;
import com.henheang.hspaceapi.payload.inbox.InboxItemRequest;
import com.henheang.hspaceapi.payload.inbox.InboxItemResponse;
import com.henheang.hspaceapi.payload.inbox.InboxItemValues;
import com.henheang.hspaceapi.repository.InboxItemRepository;
import com.henheang.hspaceapi.service.InboxItemService;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InboxItemServiceImpl implements InboxItemService {

    private static final String ALL = "all";

    private final InboxItemRepository inboxItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InboxItemResponse> list(
            UUID userId, String status, String itemType, String query, Integer limit) {
        String statusFilter = filterValue(status, InboxItemValues.STATUSES, "status");
        String typeFilter = filterValue(itemType, InboxItemValues.ITEM_TYPES, "itemType");
        int max = listLimit(limit);

        List<InboxItem> items =
                StringUtils.hasText(query)
                        ? inboxItemRepository.searchForUser(
                                userId, query.trim(), statusFilter, typeFilter, max)
                        : inboxItemRepository.findForUser(
                                userId, statusFilter, typeFilter, PageRequest.of(0, max));
        return items.stream().map(InboxItemResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InboxItemResponse get(UUID userId, UUID id) {
        return InboxItemResponse.from(findOwned(userId, id));
    }

    @Override
    @Transactional
    public InboxItemResponse create(UUID userId, InboxItemRequest request) {
        InboxItem item = new InboxItem();
        item.setUserId(userId);
        item.setTitle(blankToNull(request.getTitle()));
        item.setContent(request.getContent().trim());
        item.setItemType(request.getItemType());
        item.setTags(
                request.getTags() == null ? new ArrayList<>() : new ArrayList<>(request.getTags()));
        item.setCapturedAt(Instant.now());
        item.setEventAt(request.getEventAt());
        item.setGoalId(request.getGoalId());
        item.setStatus("inbox");
        item.setSource(request.getSource() == null ? "manual" : request.getSource());
        item.setPinned(false);
        return InboxItemResponse.from(inboxItemRepository.save(item));
    }

    @Override
    @Transactional
    public InboxItemResponse update(UUID userId, UUID id, InboxItemPatchRequest request) {
        InboxItem item = findOwned(userId, id);

        if (request.getTitle() != null) {
            item.setTitle(blankToNull(request.getTitle().orElse(null)));
        }
        if (request.getContent() != null) {
            item.setContent(required(request.getContent(), "content").trim());
        }
        if (request.getItemType() != null) {
            item.setItemType(required(request.getItemType(), "itemType"));
        }
        if (request.getTags() != null) {
            item.setTags(new ArrayList<>(required(request.getTags(), "tags")));
        }
        if (request.getEventAt() != null) {
            item.setEventAt(request.getEventAt().orElse(null));
        }
        if (request.getGoalId() != null) {
            item.setGoalId(request.getGoalId().orElse(null));
        }
        if (request.getPinned() != null) {
            item.setPinned(request.getPinned());
        }
        if (request.getStatus() != null) {
            item.setStatus(required(request.getStatus(), "status"));
        }
        if (request.getConvertedToType() != null) {
            item.setConvertedToType(request.getConvertedToType().orElse(null));
        }
        if (request.getConvertedToId() != null) {
            item.setConvertedToId(request.getConvertedToId().orElse(null));
        }
        return InboxItemResponse.from(inboxItemRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        inboxItemRepository.delete(findOwned(userId, id));
    }

    private InboxItem findOwned(UUID userId, UUID id) {
        return inboxItemRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Inbox item", "id", id));
    }

    // null, blank and "all" mean "no filter"; anything else must be an allowed value.
    private static String filterValue(String value, String allowedPattern, String name) {
        if (!StringUtils.hasText(value) || ALL.equals(value)) {
            return null;
        }
        if (!value.matches(allowedPattern)) {
            throw new BadRequestException("Unknown " + name + " filter: " + value);
        }
        return value;
    }

    private static int listLimit(Integer limit) {
        if (limit == null) {
            return InboxItemValues.MAX_LIST_LIMIT;
        }
        if (limit < 1 || limit > InboxItemValues.MAX_LIST_LIMIT) {
            throw new BadRequestException(
                    "limit must be between 1 and " + InboxItemValues.MAX_LIST_LIMIT);
        }
        return limit;
    }

    private static <T> T required(Optional<T> value, String name) {
        return value.orElseThrow(() -> new BadRequestException(name + " can't be null"));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
