package com.gnagnoohc.scms.domain.competency.dto;

import java.time.Instant;

/**
 * 과거 진단 결과 목록(GET /api/students/assessment-history) 한 행. 상세 점수는 담지 않고
 * 결과 조회 API(GET /api/students/assessment-attempts/{attemptId}/result)를 재사용하도록
 * attemptId만 내려준다(사전·사후 비교 화면도 이 attemptId 두 개를 그대로 사용).
 */
public record AssessmentHistoryResponse(
        Integer attemptId,
        Integer roundId,
        String assessmentName,
        Integer academicYear,
        String semesterCode,
        String assessmentType,
        Instant submittedAt
) {}
