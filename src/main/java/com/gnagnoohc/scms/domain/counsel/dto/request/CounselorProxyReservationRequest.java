package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 상담사가 대면·전화로 접수한 학생을 대신해 예약을 생성할 때 보내는 요청이다.
 * 학생·유형·일정 ID는 상담사가 화면에서 고른 값을 그대로 담지만, 서버가 소유권·역할 범위·정원·
 * 동의를 모두 다시 검증하므로 이 값들을 그대로 신뢰하지 않는다. consentId·counselorId·
 * processedBy처럼 서버가 스스로 정해야 하는 값은 요청으로 받지 않는다.
 */
public record CounselorProxyReservationRequest(
        @NotNull @Positive Integer studentId,
        @NotNull @Positive Integer counselingTypeId,
        @NotNull @Positive Integer scheduleId,
        @NotBlank @Size(max = 3000) String requestContent
) {
    // 원문 길이에 먼저 @Size를 적용하면 trim 후 정확히 3,000자인 정상 입력이 잘못 거절되므로,
    // Bean Validation이 검사하기 전에 여기서 먼저 앞뒤 공백을 제거해 둔다.
    public CounselorProxyReservationRequest {
        requestContent = requestContent == null ? null : requestContent.trim();
    }
}
