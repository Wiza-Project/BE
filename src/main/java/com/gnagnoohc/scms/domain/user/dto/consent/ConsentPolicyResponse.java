package com.gnagnoohc.scms.domain.user.dto.consent;

import com.gnagnoohc.scms.domain.user.entity.ConsentPolicy;

import java.time.Instant;

/** 동의 화면에 노출할 정책 1건. */
public record ConsentPolicyResponse(
        Integer consentPolicyId,
        String consentType,
        String moduleCode,
        String version,
        String title,
        String content,
        boolean required,
        Instant effectiveFrom,
        Instant effectiveTo
) {
    public static ConsentPolicyResponse from(ConsentPolicy policy) {
        return new ConsentPolicyResponse(
                policy.getConsentPolicyId(),
                policy.getConsentType(),
                policy.getModuleCode(),
                policy.getVersion(),
                policy.getTitle(),
                policy.getContent(),
                policy.isRequired(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo()
        );
    }
}
