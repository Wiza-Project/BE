package com.gnagnoohc.scms.domain.user.service.consent;

import com.gnagnoohc.scms.domain.user.dto.consent.UserConsentHistoryResponse;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.ConsentPolicy;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.ConsentPolicyRepository;
import com.gnagnoohc.scms.domain.user.repository.UserConsentRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 약관 동의/철회 트랜잭션 관리 및 ConsentVerifier 검증 로직 구현체.
 * 타 도메인은 이 클래스 대신 ConsentVerifier 인터페이스를 직접 주입받아 사용합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserConsentService implements ConsentVerifier {

    private final UserConsentRepository userConsentRepository;
    private final ConsentPolicyRepository consentPolicyRepository;
    private final AppUserRepository appUserRepository;

    /**
     * 특정 약관 정책에 동의합니다.
     * 이미 유효한 동의 내역이 존재하는 경우 멱등성을 위해 기존 내역을 그대로 반환합니다.
     */
    @Transactional
    public UserConsentHistoryResponse agree(Integer userId, Integer consentPolicyId) {
        ConsentPolicy policy = consentPolicyRepository.findById(consentPolicyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Instant now = Instant.now();
        if (!isCurrentlyEffective(policy, now)) {
            throw new BusinessException(ErrorCode.INVALID_CONSENT_POLICY);
        }

        UserConsent existing = userConsentRepository
                .findByUser_UserIdAndConsentPolicy_ConsentPolicyIdAndWithdrawnAtIsNull(userId, consentPolicyId)
                .orElse(null);
        if (existing != null) {
            return UserConsentHistoryResponse.from(existing);
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserConsent saved = userConsentRepository.save(UserConsent.create(user, policy, now));
        return UserConsentHistoryResponse.from(saved);
    }

    /**
     * 동의 내역을 철회합니다.
     * 필수 약관이라도 철회 자체는 허용하며, 권한 부전이나 이미 철회된 상태에 대해 예외를 반환합니다.
     */
    @Transactional
    public void withdraw(Integer userId, Integer userConsentId) {
        UserConsent consent = userConsentRepository.findById(userConsentId)
                .filter(c -> c.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_CONSENT_NOT_FOUND));

        if (consent.getWithdrawnAt() != null) {
            throw new BusinessException(ErrorCode.CONSENT_ALREADY_WITHDRAWN);
        }
        consent.withdraw(Instant.now());
    }

    /** 사용자의 전체 동의/철회 이력을 최신순으로 조회합니다. */
    public List<UserConsentHistoryResponse> getMyHistory(Integer userId) {
        return userConsentRepository.findByUser_UserIdOrderByConsentedAtDesc(userId).stream()
                .map(UserConsentHistoryResponse::from)
                .toList();
    }

    // ── ConsentVerifier ──────────────────────────────────────────

    @Override
    public UserConsent requireOwnedValidConsent(
            Integer userConsentId,
            Integer userId,
            ConsentModuleCode moduleCode,
            ConsentType consentType,
            Instant asOf
    ) {
        return userConsentRepository
                .findOwnedValidConsent(userConsentId, userId, moduleCode.name(), consentType.name(), asOf)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    @Override
    public boolean hasAgreedAllRequired(Integer userId, ConsentModuleCode moduleCode, Instant asOf) {
        List<ConsentPolicy> requiredPolicies = consentPolicyRepository
                .findEffectivePolicies(moduleCode.name(), asOf).stream()
                .filter(ConsentPolicy::isRequired)
                .toList();
        if (requiredPolicies.isEmpty()) {
            return true;
        }

        Set<Integer> agreedPolicyIds = userConsentRepository
                .findValidRequiredConsents(userId, moduleCode.name(), asOf).stream()
                .map(c -> c.getConsentPolicy().getConsentPolicyId())
                .collect(Collectors.toSet());

        return requiredPolicies.stream()
                .map(ConsentPolicy::getConsentPolicyId)
                .allMatch(agreedPolicyIds::contains);
    }

    @Override
    public boolean hasValidConsent(
            Integer userId, ConsentModuleCode moduleCode, ConsentType consentType, Instant asOf) {
        return userConsentRepository.existsValidConsent(
                userId, moduleCode.name(), consentType.name(), asOf);
    }

    @Override
    public Optional<UserConsent> findCurrentValidConsent(
            Integer userId, ConsentModuleCode moduleCode, ConsentType consentType, Instant asOf) {
        return userConsentRepository
                .findCurrentValidConsentCandidates(userId, moduleCode.name(), consentType.name(), asOf)
                .stream()
                .findFirst();
    }

    private boolean isCurrentlyEffective(ConsentPolicy policy, Instant asOf) {
        if (!policy.isActive() || policy.getEffectiveFrom().isAfter(asOf)) {
            return false;
        }
        return policy.getEffectiveTo() == null || policy.getEffectiveTo().isAfter(asOf);
    }
}