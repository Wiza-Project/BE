package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 공개 상담 결과 초안 저장 요청. 여기서는 NULL과 대략적인 최대 길이만 걸러내고, 앞뒤 공백 제거 후
 * 정확한 1~3,000자 규칙은 CounselingPublicResult 엔티티가 최종 검증한다(실패 시 C001).
 */
public record CounselingPublicResultSaveRequest(
        @NotNull @Size(max = 3000) String resultSummary,
        @Size(max = 3000) String actionPlan
) {
}
