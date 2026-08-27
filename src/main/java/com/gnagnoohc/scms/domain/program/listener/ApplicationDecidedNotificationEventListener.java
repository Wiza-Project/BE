package com.gnagnoohc.scms.domain.program.listener;

import com.gnagnoohc.scms.domain.program.event.ApplicationDecidedEvent;
import com.gnagnoohc.scms.domain.program.service.ApplicationStatus;
import com.gnagnoohc.scms.global.common.notification.ModuleCode;
import com.gnagnoohc.scms.global.common.notification.NotificationRequest;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.common.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 프로그램 신청 승인/반려 트랜잭션이 커밋된 뒤 학생에게 처리 결과를 인앱 알림으로 전달한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationDecidedNotificationEventListener {

    private final NotificationSender notificationSender;

    /** 알림 실패가 이미 완료된 승인/반려 처리를 다시 롤백시키지 않도록 커밋 후 발송한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ApplicationDecidedEvent event) {
        try {
            notificationSender.send(new NotificationRequest(
                    event.studentId(),
                    notificationTypeOf(event.decisionStatus()),
                    ModuleCode.PROGRAM,
                    titleOf(event.decisionStatus()),
                    contentOf(event)
            ));
        } catch (RuntimeException exception) {
            log.warn("프로그램 신청 심사 결과 알림 발송 실패. applicationId={}",
                    event.applicationId(), exception);
        }
    }

    private boolean isApproved(String decisionStatus) {
        return ApplicationStatus.APPROVED.name().equals(decisionStatus);
    }

    private NotificationType notificationTypeOf(String decisionStatus) {
        return isApproved(decisionStatus) ? NotificationType.APPROVED : NotificationType.REJECTED;
    }

    private String titleOf(String decisionStatus) {
        return isApproved(decisionStatus)
                ? "프로그램 참여 신청이 승인되었습니다"
                : "프로그램 참여 신청이 반려되었습니다";
    }

    private String contentOf(ApplicationDecidedEvent event) {
        if (isApproved(event.decisionStatus())) {
            return "'%s' 프로그램 참여 신청이 승인되었습니다.".formatted(event.programName());
        }
        return "'%s' 프로그램 참여 신청이 반려되었습니다. 사유: %s"
                .formatted(event.programName(), event.reason());
    }
}
