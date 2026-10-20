package com.gnagnoohc.scms.domain.user.repository;

import com.gnagnoohc.scms.domain.user.entity.ConsentPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConsentPolicyRepository extends JpaRepository<ConsentPolicy, Integer> {

    /**
     * 특정 모듈에서 asOf 시점 기준으로 노출 가능한(활성 + 유효기간 내) 정책 전체를 유형별로 조회한다.
     * 동의 화면(약관 목록)과 hasAgreedAllRequired() 게이트 체크가 공유해서 쓴다.
     */
    @Query("""
            select p from ConsentPolicy p
            where p.moduleCode = :moduleCode
              and p.active = true
              and p.effectiveFrom <= :asOf
              and (p.effectiveTo is null or p.effectiveTo > :asOf)
            order by p.consentType asc
            """)
    List<ConsentPolicy> findEffectivePolicies(@Param("moduleCode") String moduleCode, @Param("asOf") Instant asOf);

    /**
     *(모듈코드+정책타입)조합 내 유효정책 단건 조회
     * consent_policy 의 uq_consent_policy_type_module_version 유니크 제약과, 운영상 같은 조합의
     * 유효기간을 겹치지 않게 관리한다는 전제 하에 결과는 최대 1건이어야 한다.
     */
    @Query("""
            select p from ConsentPolicy p
            where p.consentType = :consentType
              and p.moduleCode = :moduleCode
              and p.active = true
              and p.effectiveFrom <= :asOf
              and (p.effectiveTo is null or p.effectiveTo > :asOf)
            """)
    Optional<ConsentPolicy> findCurrentPolicy(
            @Param("consentType") String consentType,
            @Param("moduleCode") String moduleCode,
            @Param("asOf") Instant asOf
    );
}
