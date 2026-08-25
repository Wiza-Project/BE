package com.gnagnoohc.scms.domain.program.event;

/**
 * 취소로 정원 슬롯이 비어 대기 1순위 학생에게 알림을 보내야 할 때, cancel()의 트랜잭션
 * 안에서 발행되는 이벤트. 대기자 조회·알림 발송은 반드시 이 트랜잭션의 커밋 이후에
 * (@TransactionalEventListener AFTER_COMMIT) 처리해야 한다 — 커밋 전에 처리하면
 * (1) 조회 실패가 아직 반영되지 않은 취소 처리까지 롤백시키거나, (2) REQUIRES_NEW로
 * 먼저 커밋되는 알림 발송만 남고 취소 자체는 이후 실패로 롤백되는 불일치가 생길 수 있다.
 */
public record WaitlistSlotOpenedEvent(Integer programId, String programName) {
}
