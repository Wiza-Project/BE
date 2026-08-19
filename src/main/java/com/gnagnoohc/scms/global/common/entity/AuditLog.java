package com.gnagnoohc.scms.global.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "audit_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id", nullable = false) private Integer auditLogId;
    @Column(name = "actor_user_id") private Integer actorUserId;
    @Column(name = "resource_type", nullable = false, length = 80) private String resourceType;
    @Column(name = "resource_id") private Integer resourceId;
    @Column(name = "action_code", nullable = false, length = 30) private String actionCode;
    @Column(name = "action_result", nullable = false, length = 20) private String actionResult;
    @Column(name = "access_reason", length = 500) private String accessReason;
    @Column(name = "ip_address", length = 45) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;
}
