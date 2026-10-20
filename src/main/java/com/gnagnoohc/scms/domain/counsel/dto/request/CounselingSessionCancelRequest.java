package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회기 취소 요청. 공백 제외 1~500자 사유를 필수로 받는다. */
public record CounselingSessionCancelRequest(
        @NotBlank @Size(max = 500) String cancellationReason
) {
}
