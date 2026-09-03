package com.gnagnoohc.scms.domain.career.dto.competency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 이력서 화면의 핵심역량 연동 결과 조회/재연동 응답.
 *
 * <p>status 세 가지: {@code READY}(연동된 최신 결과 있음), {@code UNAVAILABLE}(연동은 했으나
 * 완료 진단 없음), {@code NOT_SYNCED}(아직 한 번도 연동되지 않음.</p>
 */
public record ResumeCompetencyResponse(
        String status,
        Integer attemptId,
        String assessmentName,
        Integer academicYear,
        String semesterLabel,
        String assessmentPhase,
        Instant submittedAt,
        BigDecimal overallAverageScore,
        List<CompetencyScoreDto> scores,
        String reason,
        Instant syncedAt
) {
    public static final String STATUS_READY = "READY";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    public static final String STATUS_NOT_SYNCED = "NOT_SYNCED";

    public record CompetencyScoreDto(
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            BigDecimal convertedScore
    ) {}

    public static ResumeCompetencyResponse notSynced() {
        return new ResumeCompetencyResponse(
                STATUS_NOT_SYNCED, null, null, null, null, null, null, null, null, null, null);
    }
}
