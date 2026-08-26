package com.gnagnoohc.scms.domain.mileage.listener;

import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.event.ExternalActivityClaimDecisionEvent;
import com.gnagnoohc.scms.global.common.notification.ModuleCode;
import com.gnagnoohc.scms.global.common.notification.NotificationRequest;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.common.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/** 외부활동 심사 트랜잭션이 커밋된 뒤 학생에게 처리 결과를 인앱 알림으로 전달한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalActivityClaimNotificationEventListener {

    private final NotificationSender notificationSender;

    /** 알림 실패가 이미 완료된 심사·원장 처리를 다시 롤백시키지 않도록 커밋 후 발송한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ExternalActivityClaimDecisionEvent event) {
        try {
            notificationSender.send(new NotificationRequest(
                    event.studentId(),
                    notificationTypeOf(event.claimStatus()),
                    ModuleCode.MILEAGE,
                    titleOf(event.claimStatus()),
                    contentOf(event)
            ));
        } catch (RuntimeException exception) {
            log.warn("외부활동 마일리지 심사 결과 알림 발송 실패. claimId={}",
                    event.externalClaimId(), exception);
        }
    }

    private NotificationType notificationTypeOf(String claimStatus) {
        return ExternalActivityClaim.APPROVED_STATUS.equals(claimStatus)
                ? NotificationType.MILEAGE_EARNED
                : NotificationType.REJECTED;
    }

    private String titleOf(String claimStatus) {
        if (ExternalActivityClaim.APPROVED_STATUS.equals(claimStatus)) {
            return "외부활동 마일리지가 적립되었습니다";
        }
        if (ExternalActivityClaim.CANCELLED_STATUS.equals(claimStatus)) {
            return "외부활동 마일리지 적립이 취소되었습니다";
        }
        return "외부활동 마일리지 신청이 반려되었습니다";
    }

    private String contentOf(ExternalActivityClaimDecisionEvent event) {
        if (ExternalActivityClaim.APPROVED_STATUS.equals(event.claimStatus())) {
            return "'%s' 활동이 승인되어 %s점이 적립되었습니다."
                    .formatted(event.activityName(), event.transactionPoints());
        }
        if (ExternalActivityClaim.CANCELLED_STATUS.equals(event.claimStatus())) {
            BigDecimal cancelledPoints = event.transactionPoints() == null
                    ? BigDecimal.ZERO : event.transactionPoints().abs();
            return "'%s' 활동의 %s점 적립이 취소되었습니다. 사유: %s"
                    .formatted(event.activityName(), cancelledPoints, event.reason());
        }
        return "'%s' 외부활동 마일리지 신청이 반려되었습니다. 사유: %s"
                .formatted(event.activityName(), event.reason());
    }
}
