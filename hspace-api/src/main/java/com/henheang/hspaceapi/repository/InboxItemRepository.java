package com.henheang.hspaceapi.repository;

import com.henheang.hspaceapi.domain.InboxItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Every query is scoped by user_id: this is what replaces the Supabase RLS
// policy "own inbox items" (auth.uid() = user_id).
public interface InboxItemRepository extends JpaRepository<InboxItem, UUID> {

    Optional<InboxItem> findByIdAndUserId(UUID id, UUID userId);

    @Query(
            "SELECT i FROM InboxItem i WHERE i.userId = :userId"
                    + " AND (:status IS NULL OR i.status = :status)"
                    + " AND (:itemType IS NULL OR i.itemType = :itemType)"
                    + " ORDER BY i.capturedAt DESC")
    List<InboxItem> findForUser(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("itemType") String itemType,
            Pageable pageable);

    // Same websearch_to_tsquery('english', ...) semantics as supabase-js
    // .textSearch("search", q, { type: "websearch", config: "english" }).
    @Query(
            value =
                    "SELECT * FROM inbox_items WHERE user_id = :userId"
                            + " AND search @@ websearch_to_tsquery('english', :q)"
                            + " AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))"
                            + " AND (CAST(:itemType AS text) IS NULL"
                            + " OR item_type = CAST(:itemType AS text))"
                            + " ORDER BY captured_at DESC LIMIT :limit",
            nativeQuery = true)
    List<InboxItem> searchForUser(
            @Param("userId") UUID userId,
            @Param("q") String q,
            @Param("status") String status,
            @Param("itemType") String itemType,
            @Param("limit") int limit);
}
