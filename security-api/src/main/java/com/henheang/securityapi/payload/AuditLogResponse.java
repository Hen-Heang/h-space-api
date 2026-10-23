package com.henheang.securityapi.payload;

import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuditLogResponse {

    private UUID id;

    private AuditEventType eventType;

    private UUID userId;

    private String identifier;

    private String ipAddress;

    private String userAgent;

    private String details;

    private Instant createdAt;

    @Builder
    public AuditLogResponse(
            UUID id,
            AuditEventType eventType,
            UUID userId,
            String identifier,
            String ipAddress,
            String userAgent,
            String details,
            Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.userId = userId;
        this.identifier = identifier;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
        this.createdAt = createdAt;
    }

    public static AuditLogResponse from(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .eventType(auditLog.getEventType())
                .userId(auditLog.getUser() == null ? null : auditLog.getUser().getId())
                .identifier(auditLog.getIdentifier())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .details(auditLog.getDetails())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
