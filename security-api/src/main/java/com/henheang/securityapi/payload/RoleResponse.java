package com.henheang.securityapi.payload;

import com.henheang.securityapi.domain.Role;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RoleResponse {

    private UUID id;

    private String name;

    private String description;

    private Set<PermissionResponse> permissions;

    @Builder
    public RoleResponse(
            UUID id, String name, String description, Set<PermissionResponse> permissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.permissions = permissions;
    }

    public static RoleResponse from(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(
                        role.getPermissions().stream()
                                .map(PermissionResponse::from)
                                .collect(Collectors.toSet()))
                .build();
    }
}
