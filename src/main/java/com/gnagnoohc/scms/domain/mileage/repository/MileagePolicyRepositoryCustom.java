package com.gnagnoohc.scms.domain.mileage.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public interface MileagePolicyRepositoryCustom {

    // 활동유형+학기+버전으로 식별되는 정책 한 건을 삽입하고 생성된 mileage_policy_id를 반환한다.
    Integer insertPolicy(Integer activityTypeId,
                          String semesterCode,
                          Integer versionNo,
                          BigDecimal points,
                          BigDecimal maximumPoints,
                          LocalDate validFrom,
                          LocalDate validTo,
                          String duplicateRule,
                          String policyStatus,
                          Integer createdBy,
                          Instant now);
}
