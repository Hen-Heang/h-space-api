package com.henheang.securityapi.payload;

import com.henheang.securityapi.domain.Permission;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PermissionResponse {

    private UUID id;

    private String name;

    private String resource;

    private String action;

    private String description;

    @Builder
    public PermissionResponse(
            UUID id, String name, String resource, String action, String description) {
        this.id = id;
        this.name = name;
        this.resource = resource;
        this.action = action;
        this.description = description;
    }

    public static PermissionResponse from(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .resource(permission.getResource())
                .action(permission.getAction())
                .description(permission.getDescription())
                .build();
    }
}
