package com.gnagnoohc.scms.domain.mileage.DTO.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 교직원이 외부활동 마일리지 신청을 반려할 때 입력하는 사유다. */
public record MileageClaimRejectRequest(
        @NotBlank(message = "반려 사유는 필수입니다.")
        @Size(max = 1000, message = "반려 사유는 1000자 이내로 입력해야 합니다.")
        String reason
) {
}
