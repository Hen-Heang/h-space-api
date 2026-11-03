package com.henheang.financeapi.controller;

import com.henheang.financeapi.payload.IdResponse;
import com.henheang.financeapi.payload.lookup.CategoryPatchRequest;
import com.henheang.financeapi.payload.lookup.CategoryRequest;
import com.henheang.financeapi.service.FinanceCategoryService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Same paths and payloads as Luyra's app/api/finance/categories routes
// (docs/finance/API-CONTRACT.md #1-4). The owner always comes from the JWT,
// never the request; another user's category answers 404.
@RestController
@RequestMapping("/api/finance/categories")
@RequiredArgsConstructor
@Tag(name = "Finance categories", description = "Transaction categories (from Luyra)")
public class FinanceCategoryController extends BaseController {

    private final FinanceCategoryService categoryService;

    @Operation(summary = "List categories", description = "The caller's categories, by name.")
    @GetMapping
    public Object list(@AuthenticationPrincipal UserPrincipal principal) {
        return ok(categoryService.list(principal.getId()));
    }

    @Operation(
            summary = "Create a category",
            description =
                    "type defaults to expense; icon, color and spendingClass to null. A name the"
                            + " caller already uses (ignoring case) answers 409.")
    @PostMapping
    public Object create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CategoryRequest request) {
        return ok(categoryService.create(principal.getId(), request));
    }

    @Operation(
            summary = "Update a category",
            description =
                    "Partial update with at least one field: omitted fields are unchanged, an"
                            + " explicit null clears icon, color or spendingClass.")
    @PatchMapping("/{id}")
    public Object update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CategoryPatchRequest request) {
        return ok(categoryService.update(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Object delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        categoryService.delete(principal.getId(), id);
        return ok(new IdResponse(id));
    }
}
