package com.gnagnoohc.scms.domain.user.service;

import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
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

    private final AppUserRepository appUserRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void lock(Integer userId) {
        appUserRepository.markDormant(userId);
    }
}
