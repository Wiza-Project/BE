package com.gnagnoohc.scms.domain.counsel.listener;

import com.gnagnoohc.scms.domain.counsel.event.CounselingProposalCreatedEvent;
import com.gnagnoohc.scms.global.common.notification.ModuleCode;
import com.gnagnoohc.scms.global.common.notification.NotificationRequest;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.common.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상담 제안 생성은 제안 저장과 같은 트랜잭션에서 이벤트만 발행하고, 실제 인앱 알림은 그
 * 트랜잭션이 커밋된 뒤에만 이 리스너가 만든다. 잠금을 쥔 채로 알림 저장까지 하면 잠금 보유
 * 시간이 늘어나고, 알림 실패가 이미 커밋된 제안 생성을 되돌릴 이유도 없기 때문이다.
 * 제목·내용은 확정 문구로 고정하며 점수·결과 수준·제안 내용·학생 이름을 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CounselingProposalCreatedNotificationListener {

    private final NotificationSender notificationSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProposalCreated(CounselingProposalCreatedEvent event) {
        try {
            notificationSender.send(new NotificationRequest(
                    event.studentId(),
                    NotificationType.COUNSELING_PROPOSED,
                    ModuleCode.COUNSEL,
                    "상담 제안이 도착했습니다",
                    "학생상담 메뉴에서 상담 제안을 확인해 주세요."
            ));
        } catch (RuntimeException e) {
            // 알림은 이미 커밋된 제안 생성과 분리된 부작용이므로, 실패해도 제안 ID만 남기고 삼킨다.
            log.warn("상담 제안 생성 알림 발송에 실패했습니다. proposalId={}", event.proposalId());
        }
    }
}
