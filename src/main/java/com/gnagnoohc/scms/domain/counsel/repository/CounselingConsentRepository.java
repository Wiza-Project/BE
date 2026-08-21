package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 상담 예약이 참조할 수 있는 실제 동의 이력만 조회한다.
 * 유형별 필수 동의 정책은 아직 모델에 없으므로, 전달받은 동의 ID의 유효성만 확인한다.
 */
public interface CounselingConsentRepository extends Repository<UserConsent, Integer> {

    @Query("""
            select consent
            from UserConsent consent
            join consent.consentPolicy policy
            where consent.userConsentId = :consentId
              and consent.user.userId = :studentId
              and consent.consentedAt <= :now
              and consent.withdrawnAt is null
              and policy.active = true
              and policy.effectiveFrom <= :now
              and (policy.effectiveTo is null or policy.effectiveTo > :now)
            """)
    Optional<UserConsent> findValidByIdAndStudentId(
            @Param("consentId") Integer consentId,
            @Param("studentId") Integer studentId,
            @Param("now") Instant now
    );
}
