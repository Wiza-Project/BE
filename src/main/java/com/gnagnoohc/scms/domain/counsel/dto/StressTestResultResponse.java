package com.gnagnoohc.scms.domain.counsel.dto;

import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestResult;

import java.time.Instant;

/**
 * 결과 저장(제출) 응답이자 이력 목록의 각 항목이다.
 * result_summary는 API에서 resultDescription이라는 이름으로 명시적으로 매핑한다.
 */
public record StressTestResultResponse(
        Integer resultId,
        String testVersion,
        int totalScore,
        String resultLevel,
        String resultDescription,
        Instant testedAt
) {
    public static StressTestResultResponse from(PsychologicalTestResult result) {
        return new StressTestResultResponse(
                result.getPsychologicalTestResultId(),
                result.getTestVersion(),
                result.getTotalScore().intValueExact(),
                result.getResultLevel(),
                result.getResultSummary(),
                result.getTestedAt()
        );
    }
}
