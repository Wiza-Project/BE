package com.gnagnoohc.scms.domain.user.service.consent;

import com.gnagnoohc.scms.domain.user.entity.UserConsent;

import java.time.Instant;
import java.util.Optional;

/**
 * 타 도메인(상담, 취·창업 등)과의 통합을 위한 동의 검증 앤드포인트 인터페이스.
 * 서비스 구현체를 직접 주입받지 않고 본 인터페이스에만 의존하여 사이드 이펙트를 방지합니다.
 */
public interface ConsentVerifier {

    /**
     * 지정된 동의 ID의 소유권 및 유효성(미철회/유효기간)을 검증 후 엔티티를 반환합니다.
     * 검증 실패 시 보안을 위해 사유 구분 없이 FORBIDDEN 예외를 발생시킵니다.
     *
     * @param userConsentId 검증할 동의 PK (null 불가, 호출부 사전 검증 필요)
     * @throws com.gnagnoohc.scms.global.error.BusinessException FORBIDDEN - 검증 실패 시
     */
    UserConsent requireOwnedValidConsent(
            Integer userConsentId,
            Integer userId,
            ConsentModuleCode moduleCode,
            ConsentType consentType,
            Instant asOf
    );

    /**
     * 해당 모듈의 모든 필수(is_required=true) 약관 동의 여부를 확인합니다.
     * 미충족 시에도 예외를 던지지 않으며, 예외 처리 방식은 호출 도메인에 위임합니다.
     */
    boolean hasAgreedAllRequired(Integer userId, ConsentModuleCode moduleCode, Instant asOf);

    /**
     * 특정 (모듈, 유형) 정책에 대한 유효한 동의 존재 여부를 반환합니다.
     * 필수 약관 외 선택 약관 기반의 기능 분기 처리에 사용합니다.
     */
    boolean hasValidConsent(
            Integer userId, ConsentModuleCode moduleCode, ConsentType consentType, Instant asOf);

    /**
     * 특정 (모듈, 유형) 정책에 대해 현재 유효한 동의 엔티티를 조회합니다.
     * 동의 PK를 타 도메인 테이블의 FK 증빙 데이터로 저장해야 할 때 활용합니다.
     *
     * @return 유효 동의 엔티티 (미충족 시 Optional.empty)
     */
    Optional<UserConsent> findCurrentValidConsent(
            Integer userId, ConsentModuleCode moduleCode, ConsentType consentType, Instant asOf);
}