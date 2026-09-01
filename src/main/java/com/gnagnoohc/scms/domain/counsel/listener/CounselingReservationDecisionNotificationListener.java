package com.gnagnoohc.scms.domain.counsel.listener;

import com.gnagnoohc.scms.domain.counsel.event.CounselingReservationDecisionEvent;
import com.gnagnoohc.scms.global.common.notification.ModuleCode;
import com.gnagnoohc.scms.global.common.notification.NotificationRequest;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.common.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 예약 확정·반려는 예약·배정·회기 저장과 같은 트랜잭션에서 이벤트만 발행하고, 실제 인앱 알림은
 * 그 트랜잭션이 커밋된 뒤에만 이 리스너가 만든다. 잠금을 쥔 채로 알림 저장까지 하면 잠금 보유
 * 시간이 늘어나고, 알림 실패가 이미 확정된 예약 처리를 되돌릴 이유도 없기 때문이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CounselingReservationDecisionNotificationListener {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final NotificationSender notificationSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationDecided(CounselingReservationDecisionEvent event) {
        try {
            if (CounselingReservationDecisionEvent.CONFIRMED.equals(event.decisionStatus())) {
                sendConfirmed(event);
            } else if (CounselingReservationDecisionEvent.REJECTED.equals(event.decisionStatus())) {
                sendRejected(event);
            }
        } catch (RuntimeException e) {
            // 알림은 이미 커밋된 업무 처리와 분리된 부작용이므로, 실패해도 예약 ID만 남기고 삼킨다.
            // 재처리는 체크리스트 15의 운영 검증 범위다.
            log.warn("상담 예약 결정 알림 발송에 실패했습니다. reservationId={}", event.reservationId());
        }
    }

    private void sendConfirmed(CounselingReservationDecisionEvent event) {
        String period = "%s ~ %s".formatted(
                DATE_TIME_FORMATTER.format(event.sessionStartsAt()),
                DATE_TIME_FORMATTER.format(event.sessionEndsAt())
        );
        StringBuilder content = new StringBuilder("상담 예약이 확정되었습니다. 일정: ").append(period);
        if (event.location() != null) {
            content.append(", 장소: ").append(event.location());
        }
        notificationSender.send(new NotificationRequest(
                event.studentId(),
                NotificationType.RESERVATION_CONFIRMED,
                ModuleCode.COUNSEL,
                "상담 예약이 확정되었습니다",
                content.toString()
        ));
    }

    private void sendRejected(CounselingReservationDecisionEvent event) {
        notificationSender.send(new NotificationRequest(
                event.studentId(),
                NotificationType.REJECTED,
                ModuleCode.COUNSEL,
                "상담 예약이 반려되었습니다",
                "상담 예약이 반려되었습니다. 사유: " + event.decisionReason()
        ));
    }
}
