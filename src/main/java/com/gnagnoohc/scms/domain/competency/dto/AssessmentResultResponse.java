package com.gnagnoohc.scms.domain.competency.dto;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public record AssessmentResultResponse(
        Integer attemptId,
        Integer roundId,
        Instant submittedAt,
        BigDecimal overallAverageScore,
        // 각 score의 percentile null 여부로 유추하지 않고 별도 플래그로 내려주는 이유: percentile이 null인 원인이
        // "회차 미집계"뿐이라는 보장이 없다(향후 데이터 이상 등으로도 null이 될 수 있음). FE가 "집계 완료 전"이라는
        // 안내 문구를 띄우려면 원인이 명확한 신호가 따로 필요하다.
        boolean percentileAvailable,
        List<CompetencyResult> scores
) {
    public record CompetencyResult(
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            BigDecimal convertedScore,
            BigDecimal percentile
    ) {}

    // percentileAvailable=false(회차 집계 미완료)면 percentile 컬럼 값이 남아있어도 응답에 담지 않는다 —
    // "집계 완료된 회차만 백분위 확인 가능" 규칙을 DTO 조립 시점에서 강제한다.
    // overallAverageScore(방사형 차트 전체 평균 오버레이)는 백분위와 무관하게 내 환산점수만으로 항상 계산 가능하다.
    public static AssessmentResultResponse from(AssessmentAttempt attempt, List<AssessmentScore> scores,
                                                 boolean percentileAvailable) {
        List<CompetencyResult> results = scores.stream()
                .map(s -> new CompetencyResult(
                        s.getCompetency().getCompetencyId(),
                        s.getCompetency().getCompetencyName(),
                        s.getCompetency().getDisplayOrder(),
                        s.getConvertedScore(),
                        percentileAvailable ? s.getPercentile() : null))
                .toList();

        BigDecimal overallAverageScore = averageConvertedScore(
                scores.stream().map(AssessmentScore::getConvertedScore).toList());

        return new AssessmentResultResponse(
                attempt.getAttemptId(),
                attempt.getAssessmentRound().getAssessmentRoundId(),
                attempt.getSubmittedAt(),
                overallAverageScore,
                percentileAvailable,
                results);
    }

    // 결과 조회 응답과 이력서 연동 이벤트(AssessmentResultReadyEvent)가 전체 평균 산식을 공유하도록 분리한다.
    // 환산점수 합 / 개수, 소수 둘째 자리 HALF_UP.
    public static BigDecimal averageConvertedScore(List<BigDecimal> convertedScores) {
        BigDecimal sum = convertedScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(convertedScores.size()), 2, RoundingMode.HALF_UP);
    }
}
