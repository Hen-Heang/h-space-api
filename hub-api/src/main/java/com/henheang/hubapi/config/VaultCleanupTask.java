package com.henheang.hubapi.config;

import com.henheang.hubapi.repository.VaultItemRepository;
import com.henheang.hubapi.repository.VaultKeysetRepository;
import com.henheang.securityapi.config.ScheduledJobLock;
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

    static final String JOB = "hub-api.vault-deleted-account-purge";

    private final ScheduledJobLock jobLock;
    private final VaultItemRepository itemRepository;
    private final VaultKeysetRepository keysetRepository;

    // "-" as the cron turns the job off. The lock keeps two hub-api containers from purging at
    // once; the purge is idempotent, so a repeat run is harmless.
    @Scheduled(cron = "${hub.vault.cleanup-cron:0 17 * * * *}", zone = "${app.scheduling.zone:UTC}")
    @Transactional
    public void purgeDeletedAccounts() {
        if (!jobLock.tryAcquire(JOB)) {
            log.debug("Skipping {}: another instance is running it", JOB);
            return;
        }
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
