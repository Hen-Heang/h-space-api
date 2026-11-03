package com.henheang.financeapi.payload.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/finance/transactions/bulk - Luyra's bulkTransactionActionSchema, a union on action:
 *
 * <ul>
 *   <li>{@code {"action":"delete","ids":[...]}}
 *   <li>{@code {"action":"recategorize","ids":[...],"categoryId":"..." | null}} - categoryId must
 *       be present; null clears the category.
 * </ul>
 *
 * <p>categoryId is an Optional so "absent" (null) differs from an explicit JSON null
 * (Optional.empty()). Duplicate ids and the recategorize rules are checked by the service.
 */
@Getter
@Setter
@NoArgsConstructor
public class BulkTransactionRequest {

    public static final String DELETE = "delete";
    public static final String RECATEGORIZE = "recategorize";

    @NotNull(message = "Action is required")
    @Pattern(
            regexp = "^(delete|recategorize)$",
            message = "Action must be one of delete, recategorize")
    private String action;

    @NotNull(message = "Select at least one transaction")
    @Size(min = 1, message = "Select at least one transaction")
    @Size(max = 200, message = "Select at most 200 transactions at a time")
    private List<@NotNull(message = "Transaction id can't be null") UUID> ids;

    private Optional<UUID> categoryId;
}
