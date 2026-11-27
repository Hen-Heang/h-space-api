-- Import step: staged rows whose id Spring has already seen deleted. Read-only,
-- Spring database, run inside the import transaction after loading inbox_stage
-- (see docs/spring-migration-plan.md, Inbox cutover runbook). The result must
-- be empty. A row here means the copy would resurrect an item the user deleted
-- in Spring (for example on a second cutover after a rollback): stop and
-- reconcile that id by hand.
--
-- Tested by InboxCutoverSqlIntegrationTest (LED-09).

SELECT s.id, s.user_id, t.deleted_at
FROM inbox_stage AS s
JOIN inbox_item_tombstones AS t ON t.id = s.id
ORDER BY s.id;
