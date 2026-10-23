package com.henheang.securityapi.controller;

import com.henheang.securityapi.payload.UpdateUserRequest;
import com.henheang.securityapi.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController extends BaseController {

    private final UserService userService;

    // URL-level ".authenticated()" only proves the caller is logged in as
    // *someone* - it doesn't stop user A from reading/editing/deleting user
    // B's account by guessing an id. These annotations are the actual
    // authorization check.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Object getAllUsers() {
        return ok(userService.getAllUsers());
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @PatchMapping("/{id}")
    public Object updateUser(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest updateUserRequest) {
        return ok(userService.updateUser(id, updateUserRequest));
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @DeleteMapping("/{id}")
    public Object deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/unlock")
    public Object unlockUser(@PathVariable UUID id) {
        userService.unlockUser(id);
        return ok();
    }
}
