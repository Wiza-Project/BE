package com.gnagnoohc.scms.global.common.notification;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.Notification;
import com.gnagnoohc.scms.global.common.repository.NotificationRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * MVP1 범위: APP 채널만 실제로 동작한다. DB에 저장하는 즉시 발송이 끝난 것으로 보고
 * deliveryStatus=SENT, sentAt=now로 확정한다.
 *
 * SMS/알림톡/메일 채널은 이번 스코프에서 실제 연동하지 않는다 — 차기 확장 시 어느 채널로
 * 보낼지를 나타낼 신호가 NotificationRequest에 아직 없으므로(지금은 APP 고정), 그 필드를
 * 추가하고 channel 값에 따라 분기하는 흐름을 추가하면 된다. Notification.create()로 PENDING
 * 상태로 저장해두고, 별도 발송 스케줄러/워커가 markSent()/markFailed()로 상태를 확정하는
 * 흐름을 그대로 쓰면 된다(retryCount/failureReason 필드가 이미 그 용도로 준비돼 있음).
 *
 * 트랜잭션 격리: send()는 REQUIRES_NEW로 별도 트랜잭션에서 실행된다. 다른 도메인이 승인/반려
 * 처리 로직 마지막에 이 메서드를 호출하는 게 이 모듈의 핵심 사용 패턴인데, 기본 전파(REQUIRED)를
 * 쓰면 알림 저장 중 예외가 나는 순간 호출자의 트랜잭션이 rollback-only로 표시돼 버려서, 호출자가
 * 그 예외를 잡아 무시해도 커밋 시점에 UnexpectedRollbackException이 터지며 이미 끝난 승인 처리까지
 * 함께 롤백된다. REQUIRES_NEW로 물리적으로 분리해야 호출자가 send()를 try/catch로 감싸 "알림 발송
 * 실패가 본 업무를 막지 않게" 만들 수 있다 (LoginFailureTracker/DormantAccountLocker와 동일한 이유).
 */
@Service
@RequiredArgsConstructor
public class NotificationSenderImpl implements NotificationSender {

    private static final String CHANNEL_APP = "APP";
    private static final int TITLE_MAX_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(NotificationRequest request) {
        validate(request);

        if (!appUserRepository.existsById(request.recipientUserId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // FK로만 쓰이고 필드는 읽지 않으므로, 전체 컬럼을 읽는 findById 대신
        // SELECT 없는 프록시만 얻는 getReferenceById로 충분하다.
        AppUser recipient = appUserRepository.getReferenceById(request.recipientUserId());

        Notification notification = Notification.create(
                recipient,
                request.type().name(),
                request.moduleCode().name(),
                CHANNEL_APP,
                request.title(),
                request.content()
        );
        notification.markSent(Instant.now());
        notificationRepository.save(notification);
    }

    // 여기서 막지 않으면 title/content NOT NULL 제약 위반이 save() 시점에야 터져서,
    // GlobalExceptionHandler가 못 잡는 예외로 새 원인을 알 수 없는 500으로 응답하게 된다.
    private void validate(NotificationRequest request) {
        if (request.recipientUserId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "recipientUserId는 필수입니다.");
        }
        if (request.type() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "type은 필수입니다.");
        }
        if (request.moduleCode() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "moduleCode는 필수입니다.");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "title은 필수입니다.");
        }
        if (request.title().length() > TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "title은 " + TITLE_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "content는 필수입니다.");
        }
    }
}
