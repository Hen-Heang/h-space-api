package com.henheang.hubapi.config;

import com.henheang.hubapi.repository.VaultItemRepository;
import com.henheang.hubapi.repository.VaultKeysetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an account in security-api is a soft delete (users.deleted_at), so the vault tables' ON
 * DELETE CASCADE never fires. This purges the vault of every deleted account instead. It runs in
 * hub-api because account deletion happens in a different process (security-api), so an in-process
 * event wouldn't reach it.
 */
@Component
@RequiredArgsConstructor
public class VaultCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(VaultCleanupTask.class);

    private final VaultItemRepository itemRepository;
    private final VaultKeysetRepository keysetRepository;

    @Scheduled(cron = "${hub.vault.cleanup-cron:0 17 * * * *}")
    @Transactional
    public void purgeDeletedAccounts() {
        int items = itemRepository.deleteForDeletedUsers();
        int keysets = keysetRepository.deleteForDeletedUsers();
        if (items > 0 || keysets > 0) {
            log.info(
                    "Purged vault data of deleted accounts: {} item(s), {} keyset(s)",
                    items,
                    keysets);
        }
    }
}
