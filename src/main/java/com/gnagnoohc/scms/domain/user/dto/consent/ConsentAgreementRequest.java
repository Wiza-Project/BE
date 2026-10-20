package com.gnagnoohc.scms.domain.user.dto.consent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 사용자 본인이 특정 정책 버전에 동의할 때 보내는 요청. userId는 인증 주체에서 가져오므로 받지 않는다. */
public record ConsentAgreementRequest(
        @NotNull @Positive Integer consentPolicyId
) {
}
