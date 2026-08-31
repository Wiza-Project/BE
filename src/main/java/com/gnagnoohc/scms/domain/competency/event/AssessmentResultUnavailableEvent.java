package com.gnagnoohc.scms.domain.competency.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 취창업의 재연동 요청을 처리했으나 연동할 완료 진단 결과가 없을 때 발행하는 이벤트.
 *
 * <p>취창업 화면이 "결과 없음"과 "이벤트 처리 지연·실패"를 구분할 수 있게 하기 위한 것이다.
 * 결과가 있으면 {@link AssessmentResultReadyEvent}가, 없으면 이 이벤트가 요청의 requestId를
 * 그대로 달고 발행된다.</p>
 */
public record AssessmentResultUnavailableEvent(
        Integer studentId,
        UUID requestId,
        String reason,
        Instant occurredAt
) {
    // 네 필드 모두 필수 — 특히 requestId가 null이면 취창업이 재시도 요청과 이 응답을 연결하지 못한다.
    public AssessmentResultUnavailableEvent {
        Objects.requireNonNull(studentId, "studentId는 필수입니다");
        Objects.requireNonNull(requestId, "requestId는 필수입니다");
        Objects.requireNonNull(reason, "reason은 필수입니다");
        Objects.requireNonNull(occurredAt, "occurredAt은 필수입니다");
    }

    /** 현재 유일한 사유값 — 학생의 완료 진단 자체가 하나도 없는 경우. */
    public static final String REASON_NO_COMPLETED_ASSESSMENT = "NO_COMPLETED_ASSESSMENT";
}
