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

    /**
     * 알림 생성. deliveryStatus는 컬럼 기본값(PENDING)을 그대로 두고, 실제 발송 확정은
     * markSent()/markFailed()로 별도 처리한다 — APP 채널(NotificationSenderImpl)은 저장 직후
     * markSent()를 바로 호출해 사실상 동기 발송처럼 동작하지만, 차기 확장될 SMS/알림톡/메일
     * 채널은 PENDING으로 남겨뒀다가 스케줄러 등이 나중에 markSent()/markFailed()를 호출하는
     * 흐름을 그대로 쓸 수 있다.
     */
    public static Notification create(AppUser recipientUser, String notificationType, String moduleCode,
                                       String channel, String title, String content) {
        Notification notification = new Notification();
        notification.recipientUser = recipientUser;
        notification.notificationType = notificationType;
        notification.moduleCode = moduleCode;
        notification.channel = channel;
        notification.title = title;
        notification.content = content;
        return notification;
    }

    public void markSent(Instant sentAt) {
        this.deliveryStatus = "SENT";
        this.sentAt = sentAt;
    }

    public void markFailed(String reason) {
        this.deliveryStatus = "FAILED";
        this.failureReason = reason;
        this.retryCount = this.retryCount + 1;
    }

    /** 읽음 처리. 이미 읽은 알림에 다시 호출해도 최초 읽은 시각을 덮어쓰지 않는다. */
    public void markRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
