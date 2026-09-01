package com.gnagnoohc.scms.domain.competency.dto.response;

import java.time.Instant;

/**
 * 학생 진단 안내 화면의 "응시 가능한 회차" 목록 항목.
 * attemptId/attemptStatus는 이미 응시를 시작한 회차에만 채워지고(없으면 둘 다 null),
 * 진단 안내 조회(AssessmentIntroResponse)와 같은 의미다.
 */
public record StudentAssessmentRoundResponse(
        Integer assessmentRoundId,
        String assessmentName,
        String assessmentType,
        Instant startsAt,
        Instant endsAt,
        long questionCount,
        Integer attemptId,
        String attemptStatus
) {}
