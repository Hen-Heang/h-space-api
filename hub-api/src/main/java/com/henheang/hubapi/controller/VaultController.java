package com.henheang.hubapi.controller;

import com.henheang.hubapi.payload.vault.VaultItemCreateRequest;
import com.henheang.hubapi.payload.vault.VaultItemUpdateRequest;
import com.henheang.hubapi.payload.vault.VaultKeysetRequest;
import com.henheang.hubapi.payload.vault.VaultResetRequest;
import com.henheang.hubapi.service.VaultService;
import com.henheang.securityapi.controller.BaseController;
import com.henheang.securityapi.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * H-Vault (docs/vault-design.md, section 7). The owner is always the authenticated principal; no
 * user id is ever read from a path or body. Bodies are never logged.
 */
@RestController
@RequestMapping("/api/hub/vault")
@RequiredArgsConstructor
public class VaultController extends BaseController {

    private final VaultService vaultService;

    @GetMapping("/keyset")
    public Object getKeyset(@AuthenticationPrincipal UserPrincipal user) {
        return ok(vaultService.getKeyset(user.getId()));
    }

    @PutMapping("/keyset")
    public Object saveKeyset(
            @Valid @RequestBody VaultKeysetRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ok(vaultService.saveKeyset(user.getId(), request));
    }

    @PostMapping("/reset")
    public Object reset(
            @Valid @RequestBody VaultResetRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        vaultService.reset(user.getId(), request.getMfaCode());
        return ok();
    }

    @GetMapping("/items")
    public Object listItems(@AuthenticationPrincipal UserPrincipal user) {
        return ok(vaultService.listItems(user.getId()));
    }

    @PostMapping("/items")
    public Object createItem(
            @Valid @RequestBody VaultItemCreateRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ok(vaultService.createItem(user.getId(), request));
    }

    @PutMapping("/items/{id}")
    public Object updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody VaultItemUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ok(vaultService.updateItem(user.getId(), id, request));
    }

    @DeleteMapping("/items/{id}")
    public Object deleteItem(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user) {
        vaultService.deleteItem(user.getId(), id);
        return ok();
    }
}
