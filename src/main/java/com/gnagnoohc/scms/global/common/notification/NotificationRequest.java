package com.gnagnoohc.scms.global.common.notification;

/**
 * 알림 발송 요청. NotificationSender.send()의 유일한 파라미터.
 * recipientUserId/type/moduleCode/title/content 중 하나라도 비어있으면 NotificationSenderImpl이
 * 저장을 시도하기 전에 BusinessException(INVALID_INPUT)으로 즉시 실패시킨다 — DB 제약 위반으로
 * 새서 원인을 알 수 없는 500이 나지 않도록 하기 위함.
 *
 * @param recipientUserId 알림을 받을 사용자의 user_id (필수)
 * @param type             알림 유형(NotificationType, 필수)
 * @param moduleCode       호출한 도메인 식별자(ModuleCode, 필수)
 * @param title            알림 제목 (필수, 200자 이하)
 * @param content          알림 본문 (필수)
 */
public record NotificationRequest(
        Integer recipientUserId,
        NotificationType type,
        ModuleCode moduleCode,
        String title,
        String content
) {}
