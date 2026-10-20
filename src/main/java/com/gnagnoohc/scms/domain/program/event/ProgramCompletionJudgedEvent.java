package com.gnagnoohc.scms.domain.program.event;

/**
 * 비교과 프로그램 이수 판정이 COMPLETED로 확정된 신청 건마다 발행되는 이벤트.
 * ProgramStatusScheduler의 @Transactional 트랜잭션 안에서 동기 발행된다. 판정과 이수번호
 * (certificate_no) 채번은 judgeCompletion UPDATE 안에서 원자적으로 끝나므로, 구독 측 리스너 방식과
 * 무관하게 "판정·이수증 발급"의 원자성은 유지된다. 마일리지 적립 리스너는 부가 기능이라
 * @TransactionalEventListener(phase = AFTER_COMMIT) + 자체 트랜잭션으로 분리해 best-effort로
 * 처리한다(적립 실패가 판정을 롤백하지 않음).
 */
public record ProgramCompletionJudgedEvent(Integer applicationId) {
}
