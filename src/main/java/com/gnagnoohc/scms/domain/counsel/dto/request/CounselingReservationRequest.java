package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 학생이 직접 선택한 유형, 일정과 선택 동의 이력만 받는다.
 * 학생 ID와 예약 상태는 인증 주체와 서버 규칙으로 결정하므로 요청으로 받지 않는다.
 */
public record CounselingReservationRequest(
        @NotNull @Positive Integer counselingTypeId,
        @Positive Integer scheduleId,
        @NotNull @Positive Integer consentId,
        @NotBlank @Size(max = 3000) String requestContent
) {
    // 원문 길이에 먼저 @Size를 적용하면 trim 후 정확히 3,000자인 정상 입력이 잘못 거절되므로,
    // Bean Validation이 검사하기 전에 여기서 먼저 앞뒤 공백을 제거해 둔다.
    public CounselingReservationRequest {
        requestContent = requestContent == null ? null : requestContent.trim();
    }
}
