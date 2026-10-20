package com.gnagnoohc.scms.domain.competency.dto.response;

import java.time.Instant;

public record AssessmentIntroResponse(
        Integer assessmentRoundId,
        String assessmentName,
        Instant startsAt,
        Instant endsAt,
        long questionCount,
        long estimatedMinutes,
        // 이미 응시를 시작(attempt 생성)했다면 그 attemptId, 아직이면 null
        Integer attemptId,
        String attemptStatus
) {}
