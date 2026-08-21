package com.gnagnoohc.scms.domain.program.event;

/**
 * 비교과 프로그램 이수 판정이 COMPLETED로 확정된 신청 건마다 발행되는 이벤트.
 * (주의) ProgramStatusScheduler의 @Transactional 트랜잭션 안에서 동기 발행된다. 이 이벤트를 구독해
 * 마일리지를 적립하는 리스너가 @Async이거나 @TransactionalEventListener(phase = AFTER_COMMIT)이면
 * "판정·이수증 발급·마일리지 반영이 하나의 트랜잭션"이라는 보장이 깨지므로, 반드시 같은 트랜잭션
 * 안에서(기본 @EventListener) 처리해야 한다.
 */
public record ProgramCompletionJudgedEvent(Integer applicationId) {
}
