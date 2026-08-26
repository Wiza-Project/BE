package com.gnagnoohc.scms.domain.competency.dto;

import java.util.List;

/** sentUserIds는 실제로 발송(저장)까지 성공한 userId만 담는다 — FE가 이 목록으로 "발송완료" 표시를 채운다. */
public record AssessmentNonParticipantNotifyResponse(
        List<Integer> sentUserIds,
        int failedCount
) {}
