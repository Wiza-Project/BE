package com.gnagnoohc.scms.domain.competency.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 학생의 완료된 진단 결과가 이력서 연동용으로 준비되었음을 알리는 이벤트.
 *
 * <p>취창업(career) 도메인이 구독해 이력서 전용 읽기 모델에 적재한다. 취창업은 이 payload 외의
 * 핵심역량 테이블/서비스를 조회하지 않으므로, 이력서 화면이 필요로 하는 회차 메타·환산점수·전체
 * 평균을 모두 담아 발행한다. {@code studentId + attemptId}가 멱등 키이며, 같은 키로 재수신되면
 * 취창업은 해당 결과 행을 갱신한다.</p>
 *
 * <p>발행 경로는 세 가지다: 제출 완료 시점, 초기 백필, 취창업의 재연동 요청 처리. 앞의 두 경로에서는
 * {@link #requestId()}가 {@code null}이고, 재연동 처리에서만 요청의 requestId를 그대로 전달해
 * 취창업이 재시도 요청과 결과를 연결할 수 있게 한다.</p>
 */
public record AssessmentResultReadyEvent(
        Integer studentId,
        Integer attemptId,
        Integer assessmentRoundId,
        String assessmentName,
        Integer academicYear,
        String semesterCode,
        String semesterLabel,
        String assessmentPhase,
        Instant submittedAt,
        BigDecimal overallAverageScore,
        List<CompetencyScore> scores,
        UUID requestId
) {
    /** 역량별 환산점수. {@code displayOrder} 오름차순으로 담는다. */
    public record CompetencyScore(
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            BigDecimal convertedScore
    ) {}
}
