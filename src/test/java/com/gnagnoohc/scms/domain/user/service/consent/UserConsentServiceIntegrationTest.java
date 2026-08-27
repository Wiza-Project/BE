package com.gnagnoohc.scms.domain.user.service.consent;

import com.gnagnoohc.scms.domain.user.dto.consent.UserConsentHistoryResponse;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.ConsentPolicy;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.repository.ConsentPolicyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * findByIdForUpdate 기반으로 교체한 requireOwnedValidConsent()/withdraw()가 기존 동작(허용·거절
 * 조건, 예외 계약)을 그대로 보존하는지 확인한다. 잠금 자체의 동시성 검증은
 * UserConsentPostgresConcurrencyTest(로컬 Postgres 전용)에서 별도로 다룬다.
 */
@SpringBootTest
class UserConsentServiceIntegrationTest {

    private static final Integer CREATED_BY = 1;

    @Autowired
    private UserConsentService userConsentService;
    @Autowired
    private ConsentVerifier consentVerifier;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private ConsentPolicyRepository consentPolicyRepository;

    private Integer studentId;
    private Integer otherStudentId;
    private Instant now;

    @BeforeEach
    void setUp() {
        studentId = createStudent("본인").getUserId();
        otherStudentId = createStudent("타인").getUserId();
        now = Instant.now();
    }

    @Test
    void requireOwnedValidConsent_validConsent_returnsEntity() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));

        var result = consentVerifier.requireOwnedValidConsent(
                consentId, studentId, ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now);

        assertThat(result.getUserConsentId()).isEqualTo(consentId);
    }

    @Test
    void requireOwnedValidConsent_otherStudentsConsent_throwsForbidden() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));

        assertForbidden(() -> consentVerifier.requireOwnedValidConsent(
                consentId, otherStudentId, ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now));
    }

    @Test
    void requireOwnedValidConsent_withdrawnConsent_throwsForbidden() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));
        userConsentService.withdraw(studentId, consentId);

        assertForbidden(() -> consentVerifier.requireOwnedValidConsent(
                consentId, studentId, ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now));
    }

    @Test
    void requireOwnedValidConsent_moduleMismatch_throwsForbidden() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));

        assertForbidden(() -> consentVerifier.requireOwnedValidConsent(
                consentId, studentId, ConsentModuleCode.CAREER, ConsentType.PERSONAL_INFO, now));
    }

    @Test
    void requireOwnedValidConsent_consentTypeMismatch_throwsForbidden() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));

        assertForbidden(() -> consentVerifier.requireOwnedValidConsent(
                consentId, studentId, ConsentModuleCode.COUNSELING, ConsentType.SENSITIVE_INFO, now));
    }

    @Test
    void requireOwnedValidConsent_policyExpired_throwsForbidden() {
        // 이미 지난 유효기간의 정책은 agree() 자체가 INVALID_CONSENT_POLICY로 막으므로,
        // 동의 시점엔 유효했다가 그 뒤 정책이 개정(effectiveTo 마감)된 상황을 재현한다.
        ConsentPolicy expiring = policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO,
                true, now.minus(1, ChronoUnit.DAYS), null);
        Integer consentId = agree(expiring);
        ReflectionTestUtils.setField(expiring, "effectiveTo", now.minusSeconds(1));
        consentPolicyRepository.save(expiring);

        assertForbidden(() -> consentVerifier.requireOwnedValidConsent(
                consentId, studentId, ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now));
    }

    @Test
    void requireOwnedValidConsent_policyInactive_throwsForbidden() {
        ConsentPolicy activePolicy = policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null);
        Integer consentId = agree(activePolicy);
        ReflectionTestUtils.setField(activePolicy, "active", false);
        consentPolicyRepository.save(activePolicy);

        assertForbidden(() -> consentVerifier.requireOwnedValidConsent(
                consentId, studentId, ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now));
    }

    @Test
    void requireOwnedValidConsent_nonexistentConsentId_throwsForbidden() {
        assertForbidden(() -> consentVerifier.requireOwnedValidConsent(
                Integer.MAX_VALUE, studentId, ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now));
    }

    @Test
    void withdraw_ownedActiveConsent_setsWithdrawnAt() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));

        userConsentService.withdraw(studentId, consentId);

        UserConsentHistoryResponse history = userConsentService.getMyHistory(studentId).stream()
                .filter(h -> h.userConsentId().equals(consentId))
                .findFirst().orElseThrow();
        assertThat(history.withdrawnAt()).isNotNull();
    }

    @Test
    void withdraw_alreadyWithdrawn_throwsConsentAlreadyWithdrawn() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));
        userConsentService.withdraw(studentId, consentId);

        assertThatThrownBy(() -> userConsentService.withdraw(studentId, consentId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_ALREADY_WITHDRAWN);
    }

    @Test
    void withdraw_otherStudentsConsent_throwsUserConsentNotFound() {
        Integer consentId = agree(policy(ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, true, null, null));

        assertThatThrownBy(() -> userConsentService.withdraw(otherStudentId, consentId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_CONSENT_NOT_FOUND);
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private Integer agree(ConsentPolicy policy) {
        return userConsentService.agree(studentId, policy.getConsentPolicyId()).userConsentId();
    }

    private ConsentPolicy policy(
            ConsentModuleCode moduleCode, ConsentType consentType, boolean active, Instant from, Instant to) {
        ConsentPolicy p = BeanUtils.instantiateClass(ConsentPolicy.class);
        ReflectionTestUtils.setField(p, "consentType", consentType.name());
        ReflectionTestUtils.setField(p, "moduleCode", moduleCode.name());
        ReflectionTestUtils.setField(p, "version", "TEST-" + System.nanoTime());
        ReflectionTestUtils.setField(p, "title", "테스트 정책");
        ReflectionTestUtils.setField(p, "content", "테스트 본문");
        ReflectionTestUtils.setField(p, "required", true);
        ReflectionTestUtils.setField(p, "effectiveFrom", from != null ? from : now.minus(1, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(p, "effectiveTo", to);
        ReflectionTestUtils.setField(p, "active", active);
        ReflectionTestUtils.setField(p, "createdBy", CREATED_BY);
        return consentPolicyRepository.save(p);
    }

    private AppUser createStudent(String label) {
        AppUser user = BeanUtils.instantiateClass(AppUser.class);
        String suffix = label + System.nanoTime();
        ReflectionTestUtils.setField(user, "universityNo", "CT" + System.nanoTime());
        ReflectionTestUtils.setField(user, "userName", "동의테스트" + suffix);
        ReflectionTestUtils.setField(user, "userType", "STUDENT");
        ReflectionTestUtils.setField(user, "passwordHash", "$2a$10$dummyhashedpasswordfortest");
        ReflectionTestUtils.setField(user, "email", "consent" + System.nanoTime() + "@univ.ac.kr");
        ReflectionTestUtils.setField(user, "accountStatus", "ACTIVE");
        return appUserRepository.save(user);
    }
}
