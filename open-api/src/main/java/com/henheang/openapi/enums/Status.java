package com.henheang.openapi.enums;

import lombok.Getter;

@Getter
public enum Status {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    CANCELLED("cancelled");
    private final String value;

    Status(String value) {
        this.value = value;
    }

    public String getLabel() {
        return switch (this) {
            case PENDING -> "Pending";
            case IN_PROGRESS -> "In Progress";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
            default -> "(no label)";
        };
    }

    public static Status fromValue(String value) {
        for (Status status : Status.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
