package com.gnagnoohc.scms.domain.user.service;

import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
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

    private final AppUserRepository appUserRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(Integer userId, int newFailedCount, boolean shouldLock) {
        if (shouldLock) {
            appUserRepository.lockAccount(userId, newFailedCount, Instant.now());
        } else {
            appUserRepository.updateFailedLoginCount(userId, newFailedCount);
        }
    }
}
