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

/**
 * 휴면 전환을 별도 트랜잭션(REQUIRES_NEW)으로 커밋합니다.
 *
 * AuthService.login() 은 휴면 판정 즉시 BusinessException(ACCOUNT_DORMANT) 을 던지는데,
 * 같은 트랜잭션 안에서 계정 상태를 바꾸고 그 후 예외를 던지면 스프링 기본 정책상 트랜잭션 전체가
 * 롤백되어 "휴면으로 바뀐 것처럼 보이지만 실제 DB에는 반영되지 않는" 버그가 생깁니다.
 * 그래서 상태 전환만 별도 트랜잭션으로 분리해 즉시 커밋시킵니다.
 */
@Component
@RequiredArgsConstructor
public class DormantAccountLocker {

    private static final Logger log = LoggerFactory.getLogger(DormantAccountLocker.class);

    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void lock(Integer userId) {
        appUserRepository.markDormant(userId);
        // 실제로 DORMANT 로 전환되는 이 트랜잭션에만 기록해 중복 저장을 막는다.
        // 감사 로그 실패로 이 트랜잭션(휴면 전환)이 롤백되면 안 되므로 별도로 격리한다.
        try {
            auditLogService.recordChange(userId, "AUTH", null, AuditAction.DORMANT);
        } catch (RuntimeException e) {
            log.warn("휴면 전환 감사 로그 기록 중 예외가 발생했습니다. userId={}", userId, e);
        }
    }
}
