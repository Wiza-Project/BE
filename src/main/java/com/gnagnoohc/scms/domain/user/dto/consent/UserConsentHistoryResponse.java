package com.gnagnoohc.scms.domain.user.dto.consent;

import com.gnagnoohc.scms.domain.user.entity.UserConsent;

import java.time.Instant;

/** 마이페이지 "내 동의 이력" 1행 — 동의 시점과 정책 스냅샷, 철회 여부(withdrawnAt)를 함께 내려준다. */
public record UserConsentHistoryResponse(
        Integer userConsentId,
        Integer consentPolicyId,
        String consentType,
        String moduleCode,
        String version,
        String title,
        Instant consentedAt,
        Instant withdrawnAt
) {
    public static UserConsentHistoryResponse from(UserConsent consent) {
        var policy = consent.getConsentPolicy();
        return new UserConsentHistoryResponse(
                consent.getUserConsentId(),
                policy.getConsentPolicyId(),
                policy.getConsentType(),
                policy.getModuleCode(),
                policy.getVersion(),
                policy.getTitle(),
                consent.getConsentedAt(),
                consent.getWithdrawnAt()
        );
    }
}
