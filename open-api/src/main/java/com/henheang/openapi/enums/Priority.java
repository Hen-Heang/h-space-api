package com.henheang.openapi.enums;

import lombok.Getter;
import lombok.Setter;

public enum Priority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    @Getter @Setter private String value;

    Priority(String value) {
        this.value = value;
    }

    public static Priority fromValue(String value) {
        for (Priority priority : values()) {
            if (priority.getValue().equalsIgnoreCase(value)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown priority: " + value);
    }
}
