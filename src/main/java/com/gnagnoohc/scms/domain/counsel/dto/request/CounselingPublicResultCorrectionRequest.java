package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 공개 상담 결과 정정 요청. 여기서는 NULL·최소값 같은 형식만 걸러낸다. 문자열 길이 규칙은
 * 여기서 @Size로 검사하지 않는다 — 길이 정책이 "앞뒤 공백 제거(strip) 후" 기준인데 @Size는
 * 원문 길이로 검사하므로, 의미 있는 문자는 3,000자여도 앞뒤 공백 때문에 원문이 더 길면 유효한 값을
 * 오거절할 수 있기 때문이다. 그래서 길이 검증은 strip 후 판정하는 CounselingPublicResult 엔티티
 * 한 곳에서만 하고(실패 시 C001), 이 record는 검증 정책을 이중으로 두지 않는다.
 * expectedVersionNo는 정정 기준 버전이다 — 잠금 후 다시 읽은 최신 공개 버전과 다르면 S010(낙관적 충돌)이다.
 */
public record CounselingPublicResultCorrectionRequest(
        @NotNull @Min(1) Integer expectedVersionNo,
        @NotNull String resultSummary,
        String actionPlan,
        @NotNull String correctionReason
) {
}
