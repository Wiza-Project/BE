package com.gnagnoohc.scms.global.common.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id", nullable = false) private Integer notificationId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_user_id", nullable = false)
    private AppUser recipientUser;
    @Column(name = "notification_type", nullable = false, length = 40) private String notificationType;
    @Column(name = "module_code", nullable = false, length = 30) private String moduleCode;
    @Column(name = "channel", nullable = false, length = 20) private String channel;
    @Column(name = "title", nullable = false, length = 200) private String title;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(name = "delivery_status", nullable = false, length = 20) private String deliveryStatus = "PENDING";
    @Column(name = "retry_count", nullable = false) private Integer retryCount = 0;
    @Column(name = "failure_reason", columnDefinition = "text") private String failureReason;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "read_at") private Instant readAt;
}
