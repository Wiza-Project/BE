package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 공개 상담 결과 초안 저장 요청. 여기서는 NULL 형식만 걸러낸다. 문자열 길이는 여기서 @Size로 검사하지
 * 않는다 — 길이 정책이 "앞뒤 공백 제거(strip) 후 1~3,000자" 기준인데 @Size는 원문 길이로 검사하므로,
 * 의미 있는 문자가 3,000자여도 앞뒤 공백 때문에 원문이 더 길면 유효한 값을 오거절할 수 있다. 그래서 길이
 * 검증은 strip 후 판정하는 CounselingPublicResult 엔티티 한 곳에서만 한다(실패 시 C001).
 */
public record CounselingPublicResultSaveRequest(
        @NotNull String resultSummary,
        String actionPlan
) {
}
