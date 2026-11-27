package com.henheang.hubapi.service.impl;

import com.henheang.hubapi.domain.InboxItem;
import com.henheang.hubapi.payload.inbox.InboxItemPatchRequest;
import com.henheang.hubapi.payload.inbox.InboxItemRequest;
import com.henheang.hubapi.payload.inbox.InboxItemResponse;
import com.henheang.hubapi.payload.inbox.InboxItemValues;
import com.henheang.hubapi.repository.AppUserRepository;
import com.henheang.hubapi.repository.InboxItemRepository;
import com.henheang.hubapi.repository.InboxItemTombstoneRepository;
import com.henheang.hubapi.service.InboxItemService;
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
    private final AppUserRepository appUserRepository;
    private final InboxItemTombstoneRepository inboxItemTombstoneRepository;

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
        // The owner's anchor row (V3__app_users.sql). A Supabase-only user has no
        // legacy users row; this records only their id, never credentials.
        appUserRepository.ensure(userId);
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

    /**
     * Deletes the caller's item and leaves a tombstone (id, owner, time; no content) so a rollback
     * of the Inbox cutover can replay the delete. Order, all in this one transaction:
     *
     * <ol>
     *   <li>ownership: another user's or an unknown id is 404 and writes nothing;
     *   <li>the DELETE, flushed now rather than at commit. It is version-checked ({@code ... WHERE
     *       id = ? AND version = ?}, BaseEntity's {@code @Version}), so if the item changed since
     *       we read it, it fails here with the same 409 it always gave, before any tombstone
     *       exists;
     *   <li>the tombstone INSERT.
     * </ol>
     *
     * If anything fails, the transaction rolls back both statements: the item is never gone without
     * its tombstone, and a tombstone never exists for an item that is still there.
     */
    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        InboxItem item = findOwned(userId, id);
        inboxItemRepository.delete(item);
        inboxItemRepository.flush();
        inboxItemTombstoneRepository.record(id, userId);
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
