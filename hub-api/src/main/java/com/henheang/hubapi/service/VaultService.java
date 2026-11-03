package com.henheang.hubapi.service;

import com.henheang.hubapi.payload.vault.VaultItemCreateRequest;
import com.henheang.hubapi.payload.vault.VaultItemResponse;
import com.henheang.hubapi.payload.vault.VaultItemUpdateRequest;
import com.henheang.hubapi.payload.vault.VaultKeysetRequest;
import com.henheang.hubapi.payload.vault.VaultKeysetResponse;
import java.util.List;
import java.util.UUID;

/**
 * H-Vault storage (docs/vault-design.md). Stores and returns ciphertext only; every method takes
 * the caller's user id from the JWT and refuses accounts without MFA enabled.
 */
public interface VaultService {

    VaultKeysetResponse getKeyset(UUID userId);

    VaultKeysetResponse saveKeyset(UUID userId, VaultKeysetRequest request);

    /**
     * Deletes every item and the keyset, in one transaction, after the step-up code (null when the
     * client sends none; see {@code hub.vault.step-up-required}).
     */
    void reset(UUID userId, String mfaCode);

    List<VaultItemResponse> listItems(UUID userId);

    VaultItemResponse createItem(UUID userId, VaultItemCreateRequest request);

    VaultItemResponse updateItem(UUID userId, UUID itemId, VaultItemUpdateRequest request);

    void deleteItem(UUID userId, UUID itemId);
}
