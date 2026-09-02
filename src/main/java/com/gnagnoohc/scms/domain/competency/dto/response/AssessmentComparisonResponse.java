package com.gnagnoohc.scms.domain.competency.dto.response;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 사전·사후 비교(GET /api/students/assessment-comparison) 응답.
 * 두 응시 결과를 사전(before) → 사후(after) 순으로 담고, 역량별 변화량을 별도로 계산해 내려준다.
 * before/after 각각은 결과 조회 API 응답과 동일한 점수 구조(CompetencyResult)를 그대로 재사용해
 * FE가 겹친 방사형 차트를 결과 화면과 같은 방식으로 그릴 수 있게 한다.
 */
public record AssessmentComparisonResponse(
        ComparisonSide before,
        ComparisonSide after,
        List<CompetencyDelta> deltas
) {

    public record ComparisonSide(
            Integer attemptId,
            Integer roundId,
            String assessmentName,
            String assessmentType,
            Integer academicYear,
            String semesterCode,
            Instant submittedAt,
            BigDecimal overallAverageScore,
            boolean percentileAvailable,
            List<AssessmentResultResponse.CompetencyResult> scores
    ) {
        public static ComparisonSide of(AssessmentResultResponse result, AssessmentRound round) {
            return new ComparisonSide(
                    result.attemptId(),
                    result.roundId(),
                    round.getAssessmentName(),
                    round.getAssessmentType(),
                    round.getAcademicYear(),
                    round.getSemesterCode(),
                    result.submittedAt(),
                    result.overallAverageScore(),
                    result.percentileAvailable(),
                    result.scores());
        }
    }

    /**
     * delta = afterScore - beforeScore. 오른 역량(양수)뿐 아니라 떨어진 역량(음수)도 마스킹 없이 그대로 내려준다.
     */
    public record CompetencyDelta(
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            BigDecimal beforeScore,
            BigDecimal afterScore,
            BigDecimal delta
    ) {}
}
