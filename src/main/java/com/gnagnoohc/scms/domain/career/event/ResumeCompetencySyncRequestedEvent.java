package com.gnagnoohc.scms.domain.career.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 이력서 화면에서 최신 핵심역량 snapshot이 없거나 학생이 재시도를 선택했을 때 취창업(career)이
 * 발행하는 재연동 요청 이벤트. 핵심역량(competency) 도메인이 구독해, 학생의 완료 진단 최신 1건이
 * 있으면 {@code AssessmentResultReadyEvent}를, 없으면 {@code AssessmentResultUnavailableEvent}를
 * requestId를 달아 재발행한다.
 *
 * <p>(취창업 확인 필요) 이 클래스는 컴파일·테스트를 위해 핵심역량 측에서 계약대로 최소 record로
 * 생성한 것이다. 취창업이 실제 발행부를 구현할 때 아래를 맞춰야 한다.
 * <ul>
 *   <li>FQN: {@code com.gnagnoohc.scms.domain.career.event.ResumeCompetencySyncRequestedEvent}</li>
 *   <li>필드: {@code studentId}(Integer), {@code requestId}(UUID), {@code requestedAt}(Instant) — 전부 필수</li>
 *   <li>발행 시점: 핵심역량 리스너가 기본 동기 {@code @EventListener}로 받고 자체 트랜잭션을 연다.
 *       즉, 이 이벤트는 취창업 트랜잭션 커밋 후(또는 비트랜잭션 컨텍스트)에서 발행되어야 한다 —
 *       취창업 트랜잭션 안에서 발행하면 핵심역량 리스너가 그 트랜잭션에 참여하게 된다.</li>
 * </ul>
 */
public record ResumeCompetencySyncRequestedEvent(
        Integer studentId,
        UUID requestId,
        Instant requestedAt
) {
}
