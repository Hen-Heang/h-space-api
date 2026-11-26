package com.henheang.financeapi.controller;

import com.henheang.financeapi.payload.IdResponse;
import com.henheang.financeapi.payload.lookup.PaymentMethodPatchRequest;
import com.henheang.financeapi.payload.lookup.PaymentMethodRequest;
import com.henheang.financeapi.service.FinancePaymentMethodService;
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

// Same paths and payloads as Luyra's app/api/finance/payment-methods routes
// (docs/finance/API-CONTRACT.md #5-8).
@RestController
@RequestMapping("/api/finance/payment-methods")
@RequiredArgsConstructor
@Tag(name = "Finance payment methods", description = "Payment-method labels (from Luyra)")
public class FinancePaymentMethodController extends BaseController {

    private final FinancePaymentMethodService paymentMethodService;

    @Operation(summary = "List payment methods", description = "The caller's, by name.")
    @GetMapping
    public Object list(@AuthenticationPrincipal UserPrincipal principal) {
        return ok(paymentMethodService.list(principal.getId()));
    }

    @Operation(
            summary = "Create a payment method",
            description = "A name the caller already uses (ignoring case) answers 409.")
    @PostMapping
    public Object create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentMethodRequest request) {
        return ok(paymentMethodService.create(principal.getId(), request));
    }

    @Operation(
            summary = "Update a payment method",
            description =
                    "Partial update with at least one field; an explicit null icon clears it.")
    @PatchMapping("/{id}")
    public Object update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody PaymentMethodPatchRequest request) {
        return ok(paymentMethodService.update(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Object delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        paymentMethodService.delete(principal.getId(), id);
        return ok(new IdResponse(id));
    }
}
