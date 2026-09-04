package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageGradeResponse;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 학생의 누적 확정 마일리지를 등급 정책과 비교해 현재 등급을 계산한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageGradeService {

    private static final String GRADE_BENEFIT_TYPE = "GRADE";
    private static final String ALL_SEMESTER_CODE = "ALL";

    private final MileageTransactionRepository mileageTransactionRepository;
    private final MileageBenefitPolicyRepository mileageBenefitPolicyRepository;

    /** 선택 학기에 적용되는 등급 정책으로 학생의 누적 마일리지 등급을 조회한다. */
    public MileageGradeResponse getGrade(
            Integer studentId,
            String semesterCode
    ) {
        String selectedSemesterCode = validateSemester(semesterCode);
        BigDecimal cumulativePoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudent(studentId));

        List<MileageBenefitPolicy> policies = mileageBenefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndSemesterCodeInOrderByMinimumPointsAsc(
                        GRADE_BENEFIT_TYPE,
                        List.of(selectedSemesterCode, ALL_SEMESTER_CODE));

        List<MileageBenefitPolicy> effectivePolicies = removeDuplicateThresholds(
                policies, selectedSemesterCode);

        MileageBenefitPolicy currentPolicy = effectivePolicies.stream()
                .filter(policy -> policy.getMinimumPoints().compareTo(cumulativePoints) <= 0)
                .reduce((first, second) -> second)
                .orElse(null);

        MileageBenefitPolicy nextPolicy = effectivePolicies.stream()
                .filter(policy -> policy.getMinimumPoints().compareTo(cumulativePoints) > 0)
                .findFirst()
                .orElse(null);

        BigDecimal pointsToNextGrade = nextPolicy == null
                ? BigDecimal.ZERO
                : nextPolicy.getMinimumPoints().subtract(cumulativePoints).max(BigDecimal.ZERO);

        return new MileageGradeResponse(
                cumulativePoints,
                toGrade(currentPolicy),
                toGrade(nextPolicy),
                pointsToNextGrade
        );
    }

    /** 같은 최소 점수의 학기 정책과 연간 정책이 함께 있으면 선택 학기 정책을 우선한다. */
    private List<MileageBenefitPolicy> removeDuplicateThresholds(
            List<MileageBenefitPolicy> policies,
            String selectedSemesterCode
    ) {
        List<MileageBenefitPolicy> sortedPolicies = policies.stream()
                .filter(policy -> policy.getMinimumPoints() != null)
                .sorted(Comparator.comparing(MileageBenefitPolicy::getMinimumPoints))
                .toList();

        List<MileageBenefitPolicy> effectivePolicies = new ArrayList<>();
        for (MileageBenefitPolicy policy : sortedPolicies) {
            if (effectivePolicies.isEmpty()) {
                effectivePolicies.add(policy);
                continue;
            }

            int lastIndex = effectivePolicies.size() - 1;
            MileageBenefitPolicy previous = effectivePolicies.get(lastIndex);
            if (previous.getMinimumPoints().compareTo(policy.getMinimumPoints()) != 0) {
                effectivePolicies.add(policy);
                continue;
            }

            if (ALL_SEMESTER_CODE.equalsIgnoreCase(previous.getSemesterCode())
                    && selectedSemesterCode.equalsIgnoreCase(policy.getSemesterCode())) {
                effectivePolicies.set(lastIndex, policy);
            }
        }
        return effectivePolicies;
    }

    private MileageGradeResponse.Grade toGrade(MileageBenefitPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new MileageGradeResponse.Grade(
                policy.getBenefitPolicyId(),
                policy.getBenefitName(),
                policy.getMinimumPoints(),
                policy.getSemesterCode()
        );
    }

    /** 요청 학기가 실제 개별 학기인지 확인하고 공백을 제거한다. */
    private String validateSemester(String semesterCode) {
        if (semesterCode == null || semesterCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기 정보가 올바르지 않습니다.");
        }

        String normalizedSemesterCode = semesterCode.trim();
        if (ALL_SEMESTER_CODE.equalsIgnoreCase(normalizedSemesterCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기는 개별 학기로 지정해야 합니다.");
        }
        return normalizedSemesterCode;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
