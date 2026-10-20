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

    /**
     * 감사 로그는 생성만 허용한다. 수정·삭제 메서드를 두지 않아 애플리케이션 코드에서는
     * 이미 남은 이력을 변경할 수 없게 한다.
     */
    public static AuditLog create(
            Integer actorUserId,
            String resourceType,
            Integer resourceId,
            String actionCode,
            String actionResult,
            String accessReason,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.actorUserId = actorUserId;
        auditLog.resourceType = resourceType;
        auditLog.resourceId = resourceId;
        auditLog.actionCode = actionCode;
        auditLog.actionResult = actionResult;
        auditLog.accessReason = accessReason;
        auditLog.ipAddress = ipAddress;
        auditLog.userAgent = userAgent;
        return auditLog;
    }
}
