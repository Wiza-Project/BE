package com.gnagnoohc.scms.domain.user.service;

import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.service.AuditAction;
import com.gnagnoohc.scms.global.common.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 로그인 실패 횟수 반영을 별도 트랜잭션(REQUIRES_NEW)으로 커밋합니다.
 *
 * AuthService.login() 은 비밀번호 불일치 시 곧바로 BusinessException(PASSWORD_MISMATCH) 을
 * 던지는데, 같은 트랜잭션 안에서 실패 횟수를 늘리고 그 후 예외를 던지면 스프링 기본 정책상
 * 트랜잭션 전체가 롤백되어 "카운트가 오른 것처럼 보이지만 실제 DB에는 반영되지 않는" 버그가
 * 생깁니다(DormantAccountLocker와 동일한 문제). 그래서 카운트 반영만 별도 트랜잭션으로 분리해
 * 즉시 커밋시킵니다.
 */
@Component
@RequiredArgsConstructor
public class LoginFailureTracker {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureTracker.class);

    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(Integer userId, int newFailedCount, boolean shouldLock) {
        if (shouldLock) {
            int updatedRows = appUserRepository.lockAccount(userId, newFailedCount, Instant.now());
            if (updatedRows == 0) {
                // 동시에 들어온 다른 요청이 이미 LOCKED 로 전환시켰다는 뜻이므로 중복 기록하지 않는다.
                return;
            }
            // 실제로 이 호출이 LOCKED 전환을 일으킨 경우에만 기록해 중복 저장을 막는다.
            // 감사 로그 실패로 이 트랜잭션(계정 잠금)이 롤백되면 안 되므로 별도로 격리한다.
            try {
                auditLogService.recordChange(userId, "AUTH", null, AuditAction.LOCK);
            } catch (RuntimeException e) {
                log.warn("계정 잠금 감사 로그 기록 중 예외가 발생했습니다. userId={}", userId, e);
            }
        } else {
            appUserRepository.updateFailedLoginCount(userId, newFailedCount);
        }
    }
}
