package com.henheang.openapi.enums;

public enum PermissionLevel {
    VIEW("view"),
    EDIT("edit"),
    ADMIN("admin");

    private final String value;

    private PermissionLevel(String value) {
        this.value = value;
    }

    public static PermissionLevel fromValue(String value) {
        for (PermissionLevel level : PermissionLevel.values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown permission level: " + value);
    }
}
