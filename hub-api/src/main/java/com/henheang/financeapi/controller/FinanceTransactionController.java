package com.henheang.financeapi.controller;

import com.henheang.financeapi.payload.IdResponse;
import com.henheang.financeapi.payload.transaction.BulkTransactionRequest;
import com.henheang.financeapi.payload.transaction.TransactionListQuery;
import com.henheang.financeapi.payload.transaction.TransactionRequest;
import com.henheang.financeapi.service.FinanceTransactionService;
import com.henheang.securityapi.controller.BaseController;
import com.henheang.securityapi.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Same paths and payloads as Luyra's app/api/finance/transactions routes
// (docs/finance/API-CONTRACT.md #10-13). The owner always comes from the JWT;
// another user's transaction, category or payment method answers 404.
@RestController
@RequestMapping("/api/finance/transactions")
@RequiredArgsConstructor
@Tag(name = "Finance transactions", description = "Income and expense entries (from Luyra)")
public class FinanceTransactionController extends BaseController {

    private final FinanceTransactionService transactionService;

    @Operation(
            summary = "List transactions",
            description =
                    "Dates in [start, end) (end exclusive). Optional type, categoryId,"
                            + " paymentMethodId, amountMin/amountMax (KRW, inclusive), search"
                            + " (description, case-insensitive), sort (date_desc default,"
                            + " date_asc, amount_desc, amount_asc), page (0-based). 20 per page;"
                            + " hasMore says whether another page exists.")
    @GetMapping
    public Object list(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @ModelAttribute TransactionListQuery query) {
        return ok(transactionService.list(principal.getId(), query));
    }

    @Operation(
            summary = "Create a transaction",
            description =
                    "KRW: amountKrw (whole won). USD: originalAmount (<= 2 decimals) and"
                            + " exchangeRate; amountKrw is computed as originalAmount x"
                            + " exchangeRate rounded half-up, and any amountKrw sent is ignored.")
    @PostMapping
    public Object create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TransactionRequest request) {
        return ok(transactionService.create(principal.getId(), request));
    }

    @Operation(
            summary = "Replace a transaction",
            description =
                    "Same body as create, and every field is replaced: an omitted categoryId,"
                            + " paymentMethodId or note becomes null (Luyra's PATCH semantics).")
    @PatchMapping("/{id}")
    public Object update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        return ok(transactionService.update(principal.getId(), id, request));
    }

    @Operation(
            summary = "Delete or recategorize many transactions",
            description =
                    "{action: delete, ids} or {action: recategorize, ids, categoryId (null clears"
                            + " it)}. 1-200 distinct ids. Ids that aren't the caller's are skipped;"
                            + " affected counts the rows that changed. Recategorize needs the"
                            + " caller's category, of a type that fits every selected row.")
    @PostMapping("/bulk")
    public Object bulk(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BulkTransactionRequest request) {
        return ok(transactionService.bulk(principal.getId(), request));
    }

    @Operation(
            summary = "Suggest descriptions",
            description =
                    "Past descriptions containing q (1-100 chars), at most 6, from the last 365"
                            + " days (Asia/Seoul). Ranked by uses, tripled for a prefix match, then"
                            + " recency. type filters to income or expense.")
    @GetMapping("/suggestions")
    public Object suggestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type) {
        return ok(transactionService.suggest(principal.getId(), q, type));
    }

    @DeleteMapping("/{id}")
    public Object delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        transactionService.delete(principal.getId(), id);
        return ok(new IdResponse(id));
    }
}
