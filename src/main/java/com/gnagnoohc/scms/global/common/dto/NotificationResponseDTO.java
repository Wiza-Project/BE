package com.gnagnoohc.scms.global.common.dto;

import com.gnagnoohc.scms.global.common.entity.Notification;

import java.time.Instant;

public record NotificationResponseDTO(
        Integer notificationId,
        String notificationType,
        String moduleCode,
        String title,
        String content,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponseDTO from(Notification notification) {
        return new NotificationResponseDTO(
                notification.getNotificationId(),
                notification.getNotificationType(),
                notification.getModuleCode(),
                notification.getTitle(),
                notification.getContent(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
