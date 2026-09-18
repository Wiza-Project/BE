package com.gnagnoohc.scms.domain.competency.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 핵심역량 진단 결과의 AI 해석·보완 방향·프로그램 안내 응답. */
public record CompetencyAiChatResponse(
        String reply,
        ResultSummary resultSummary,
        List<CompetencyInsight> focusCompetencies,
        List<ProgramRecommendation> programRecommendations,
        List<String> nextActions
) {

    public record ResultSummary(
            Integer attemptId,
            BigDecimal overallAverageScore,
            Instant submittedAt,
            boolean percentileAvailable
    ) {}

    /** 서버가 기존 취약역량 선정 규칙으로 고른 보완 대상 역량. */
    public record CompetencyInsight(
            Integer competencyId,
            String competencyName,
            BigDecimal convertedScore,
            String judgment,
            String recommendation
    ) {}

    /** 기존 비교과 추천 서비스가 실제 모집중으로 확인한 프로그램만 담는다. */
    public record ProgramRecommendation(
            Integer programId,
            String programName,
            String competencyName,
            String programTypeName,
            Instant recruitmentEndsAt,
            Integer remainingCapacity,
            String myApplicationStatusLabel
    ) {}
}
