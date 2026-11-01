package com.henheang.hubapi.service.impl;

import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.hubapi.domain.VaultItem;
import com.henheang.hubapi.domain.VaultKeyset;
import com.henheang.hubapi.payload.vault.VaultItemCreateRequest;
import com.henheang.hubapi.payload.vault.VaultItemResponse;
import com.henheang.hubapi.payload.vault.VaultItemUpdateRequest;
import com.henheang.hubapi.payload.vault.VaultKeysetRequest;
import com.henheang.hubapi.payload.vault.VaultKeysetResponse;
import com.henheang.hubapi.repository.VaultItemRepository;
import com.henheang.hubapi.repository.VaultKeysetRepository;
import com.henheang.hubapi.service.VaultService;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.BusinessException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    /** Far above what one person keeps; stops a runaway client filling the table. */
    static final int MAX_ITEMS_PER_USER = 1000;

    private final VaultKeysetRepository keysetRepository;
    private final VaultItemRepository itemRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public VaultKeysetResponse getKeyset(UUID userId) {
        requireMfa(userId);
        return keysetRepository
                .findById(userId)
                .map(VaultKeysetResponse::from)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        StatusCode.NOT_FOUND, "Vault isn't set up yet."));
    }

    @Override
    @Transactional
    public VaultKeysetResponse saveKeyset(UUID userId, VaultKeysetRequest request) {
        requireMfa(userId);
        Instant now = Instant.now();
        VaultKeyset keyset = keysetRepository.findById(userId).orElse(null);
        boolean creating = keyset == null;
        if (creating) {
            if (request.getVersion() != null) {
                throw conflict("Vault isn't set up yet; create it without a version.");
            }
            // Items are encrypted under the vault key in the keyset that's
            // gone; a new keyset can't read them. Only reset removes both.
            if (itemRepository.countByUserId(userId) > 0) {
                throw conflict("Reset the vault before creating a new one.");
            }
            keyset = new VaultKeyset();
            keyset.setUserId(userId);
            keyset.setCreatedAt(now);
        } else if (!Objects.equals(request.getVersion(), keyset.getVersion())) {
            throw conflict("The vault was changed on another device. Reload and try again.");
        }
        keyset.setKdf(request.getKdf());
        keyset.setKdfIterations(request.getKdfIterations());
        keyset.setKdfSalt(request.getKdfSalt());
        keyset.setWrappedKey(request.getWrappedKey());
        keyset.setRecoveryWrappedKey(request.getRecoveryWrappedKey());
        keyset.setUpdatedAt(now);
        VaultKeyset saved;
        try {
            saved = keysetRepository.saveAndFlush(keyset);
        } catch (DataIntegrityViolationException e) {
            // Two devices creating the vault at the same moment.
            throw conflict("The vault was changed on another device. Reload and try again.");
        }
        auditLogService.log(
                creating
                        ? AuditEventType.VAULT_KEYSET_CREATED
                        : AuditEventType.VAULT_KEYSET_CHANGED,
                userId,
                null);
        return VaultKeysetResponse.from(saved);
    }

    @Override
    @Transactional
    public void reset(UUID userId) {
        requireMfa(userId);
        int items = itemRepository.deleteByUserId(userId);
        keysetRepository.deleteByUserId(userId);
        auditLogService.log(AuditEventType.VAULT_RESET, userId, null, "items=" + items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VaultItemResponse> listItems(UUID userId) {
        requireMfa(userId);
        return itemRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(VaultItemResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public VaultItemResponse createItem(UUID userId, VaultItemCreateRequest request) {
        requireMfa(userId);
        if (!keysetRepository.existsById(userId)) {
            throw conflict("Set up the vault before adding items.");
        }
        if (itemRepository.countByUserId(userId) >= MAX_ITEMS_PER_USER) {
            throw conflict("The vault is full (" + MAX_ITEMS_PER_USER + " items).");
        }
        // Same answer whether the id is this user's or someone else's, so an id
        // can't be used to probe other vaults.
        if (itemRepository.existsById(request.getId())) {
            throw conflict("An item with this id already exists.");
        }
        Instant now = Instant.now();
        VaultItem item = new VaultItem();
        item.setId(request.getId());
        item.setUserId(userId);
        item.setPayloadCiphertext(request.getPayloadCiphertext());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        VaultItem saved;
        try {
            saved = itemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent create of the same id. The
            // database's message can quote the row, so it isn't passed on.
            throw conflict("An item with this id already exists.");
        }
        auditLogService.log(
                AuditEventType.VAULT_ITEM_CREATED, userId, null, "item=" + item.getId());
        return VaultItemResponse.from(saved);
    }

    @Override
    @Transactional
    public VaultItemResponse updateItem(UUID userId, UUID itemId, VaultItemUpdateRequest request) {
        requireMfa(userId);
        VaultItem item = findOwnItem(userId, itemId);
        if (!Objects.equals(request.getVersion(), item.getVersion())) {
            throw conflict("This item was changed on another device. Reload and try again.");
        }
        item.setPayloadCiphertext(request.getPayloadCiphertext());
        item.setUpdatedAt(Instant.now());
        VaultItem saved = itemRepository.saveAndFlush(item);
        auditLogService.log(AuditEventType.VAULT_ITEM_UPDATED, userId, null, "item=" + itemId);
        return VaultItemResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteItem(UUID userId, UUID itemId) {
        requireMfa(userId);
        itemRepository.delete(findOwnItem(userId, itemId));
        auditLogService.log(AuditEventType.VAULT_ITEM_DELETED, userId, null, "item=" + itemId);
    }

    // Another user's item is "not found", never "forbidden", so ids don't leak.
    private VaultItem findOwnItem(UUID userId, UUID itemId) {
        return itemRepository
                .findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item", "id", itemId));
    }

    // Read on every call rather than trusted from the token: turning MFA off
    // must close the vault immediately, not when the access token expires.
    private void requireMfa(UUID userId) {
        boolean mfaEnabled = userRepository.findById(userId).map(User::isMfaEnabled).orElse(false);
        if (!mfaEnabled) {
            throw new BusinessException(StatusCode.VAULT_MFA_REQUIRED);
        }
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(StatusCode.CONFLICT, message);
    }
}
