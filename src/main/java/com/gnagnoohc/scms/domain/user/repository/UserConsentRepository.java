package com.gnagnoohc.scms.domain.user.repository;

import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Integer> {

    /**
     * ConsentVerifier.requireOwnedValidConsent(), withdraw() 전용 조회.
     * 동의 행만 잠그고 consentPolicy는 잠그지 않는다 — 정책은 호출부에서 지연 로딩한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserConsent c where c.userConsentId = :userConsentId")
    Optional<UserConsent> findByIdForUpdate(@Param("userConsentId") Integer userConsentId);

    /**
     * 특정 정책에 대해 사용자가 현재 유효하게 들고 있는 동의 1건.
     * agree() 의 중복 동의 방지, withdraw() 대상 조회에 쓴다.
     */
    Optional<UserConsent> findByUser_UserIdAndConsentPolicy_ConsentPolicyIdAndWithdrawnAtIsNull(
            Integer userId, Integer consentPolicyId);

    /** 마이페이지 "내 동의 이력" — 철회 포함 전체를 최신순으로. */
    List<UserConsent> findByUser_UserIdOrderByConsentedAtDesc(Integer userId);

    /**
     * hasAgreedAllRequired() 게이트에서 쓰는, 특정 모듈의 필수(is_required=true) 정책 중
     * 이 사용자가 asOf 시점에 유효하게 동의한 것들만 모은다.
     */
    @Query("""
            select c from UserConsent c
            join c.consentPolicy p
            where c.user.userId = :userId
              and p.moduleCode = :moduleCode
              and p.required = true
              and p.active = true
              and p.effectiveFrom <= :asOf
              and (p.effectiveTo is null or p.effectiveTo > :asOf)
              and c.withdrawnAt is null
            """)
    List<UserConsent> findValidRequiredConsents(
            @Param("userId") Integer userId,
            @Param("moduleCode") String moduleCode,
            @Param("asOf") Instant asOf
    );

    /**
     * hasValidConsent() 용 — 특정 (모듈, 유형) 정책에 대한 유효한 동의가 하나라도 있는지.
     * 선택 동의 기반 기능 분기
     */
    @Query("""
            select count(c) > 0 from UserConsent c
            join c.consentPolicy p
            where c.user.userId = :userId
              and p.moduleCode = :moduleCode
              and p.consentType = :consentType
              and p.active = true
              and p.effectiveFrom <= :asOf
              and (p.effectiveTo is null or p.effectiveTo > :asOf)
              and c.withdrawnAt is null
            """)


    boolean existsValidConsent(
            @Param("userId") Integer userId,
            @Param("moduleCode") String moduleCode,
            @Param("consentType") String consentType,
            @Param("asOf") Instant asOf
    );

    /*
     * 역할: 특정 모듈/유형 약관에 대해 유효한 동의가 존재하느냐의 여부(boolean) 반환
     * 동작: count(c) > 0 쿼리를 통해 존재 여부만 빠르게 평가합니다.
     * 활용: 선택 동의 항목(예: 마케팅 수신, AI 맞춤 추천 등)에 따른 기능 제공 여부를 분기할 때 사용합니다.
     */
    @Query("""
            select c from UserConsent c
            join fetch c.consentPolicy p
            where c.user.userId = :userId
              and p.moduleCode = :moduleCode
              and p.consentType = :consentType
              and p.active = true
              and p.effectiveFrom <= :asOf
              and (p.effectiveTo is null or p.effectiveTo > :asOf)
              and c.withdrawnAt is null
            order by c.consentedAt desc
            """)
    List<UserConsent> findCurrentValidConsentCandidates(
            @Param("userId") Integer userId,
            @Param("moduleCode") String moduleCode,
            @Param("consentType") String consentType,
            @Param("asOf") Instant asOf
    );
}
