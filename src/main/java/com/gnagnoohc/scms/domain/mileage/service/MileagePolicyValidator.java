package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

/** 마일리지 정책의 공통 적용 조건을 검증한다. */
@Component
public class MileagePolicyValidator {

    private static final String ACTIVE = "ACTIVE";
    private static final String ALL = "ALL";

    /**
     * 기준일과 학기에 정책을 적용할 수 있는지 확인한다.
     * semesterCode가 비어 있으면 학기 정보를 제공하지 않는 기존 호출 호환을 위해 학기 검사는 생략한다.
     */
    public boolean isApplicable(MileagePolicy policy, LocalDate asOfDate, String semesterCode) {
        return policy != null
                && ACTIVE.equalsIgnoreCase(policy.getPolicyStatus())
                && policy.getActivityType() != null
                && policy.getActivityType().isActive()
                && policy.getPoints() != null
                && policy.getPoints().signum() > 0
                && policy.isApplicableOn(asOfDate)
                && isSemesterApplicable(policy.getSemesterCode(), semesterCode);
    }

    private boolean isSemesterApplicable(String policySemesterCode, String semesterCode) {
        if (semesterCode == null || semesterCode.isBlank()) {
            return true;
        }

        String normalizedPolicySemesterCode = normalize(policySemesterCode);
        String normalizedSemesterCode = normalize(semesterCode);
        return ALL.equals(normalizedPolicySemesterCode)
                || normalizedSemesterCode.equals(normalizedPolicySemesterCode);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
