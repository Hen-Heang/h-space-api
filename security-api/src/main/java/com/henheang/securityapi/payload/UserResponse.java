package com.henheang.securityapi.payload;

import java.util.List;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class UserResponse {

    private UUID id;

    private String name;

    private String username;

    private String email;

    private String phoneNumber;

    private Boolean emailVerified;

    private String imageUrl;

    private String provider;

    // Role names, e.g. ROLE_USER / ROLE_ADMIN. Set by GET /api/auth/user so
    // a client can show admin-only screens; null where it isn't filled in.
    private List<String> roles;

    @Builder
    public UserResponse(
            UUID id,
            String name,
            String username,
            String email,
            String phoneNumber,
            Boolean emailVerified,
            String imageUrl,
            String provider) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.emailVerified = emailVerified;
        this.imageUrl = imageUrl;
        this.provider = provider;
    }
}
