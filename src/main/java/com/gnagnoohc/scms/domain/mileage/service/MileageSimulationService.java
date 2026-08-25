package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageSimulationResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageSimulationRequest;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 학생의 실제 마일리지와 예정 활동을 조합해 예상 점수만 계산한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageSimulationService {

    private static final String ALL_SEMESTER_CODE = "ALL";

    private final MileageBenefitPolicyRepository benefitPolicyRepository;
    private final MileagePolicyRepository mileagePolicyRepository;
    private final MileageTransactionRepository mileageTransactionRepository;

    /** 목표 정책과 현재 선택 가능한 활동 목록을 조회한다. */
    public MileageSimulationResponse.Options getOptions(
            Integer studentId,
            Integer academicYear,
            String semesterCode
    ) {
        String normalizedSemesterCode = validatePeriod(academicYear, semesterCode);
        BigDecimal periodPoints = getPeriodPoints(studentId, academicYear, normalizedSemesterCode);

        List<MileageBenefitPolicy> benefitPolicies = benefitPolicyRepository
                .findByActiveTrueAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        academicYear, semesterCodes(normalizedSemesterCode));

        List<MileageSimulationResponse.TargetOption> targets = benefitPolicies.stream()
                .map(policy -> toTargetOption(
                        studentId, academicYear, normalizedSemesterCode, periodPoints, policy))
                .toList();

        List<MileageSimulationResponse.ActivityOption> activities = findAvailablePolicies(
                        academicYear, normalizedSemesterCode)
                .stream()
                .map(this::toActivityOption)
                .toList();

        return new MileageSimulationResponse.Options(
                new MileageSimulationResponse.Period(academicYear, normalizedSemesterCode),
                periodPoints,
                targets,
                activities);
    }

    /** 목표와 예정 활동을 반영한 결과를 계산한다. 거래 원장이나 신청 이력은 변경하지 않는다. */
    public MileageSimulationResponse.Result simulate(
            Integer studentId,
            MileageSimulationRequest request
    ) {
        String normalizedSemesterCode = validatePeriod(
                request.academicYear(), request.semesterCode());
        validateTargetInput(request);

        MileageBenefitPolicy targetPolicy = findTargetPolicy(request);
        validateTargetPeriod(targetPolicy, request.academicYear(), normalizedSemesterCode);

        BigDecimal currentPoints = targetPolicy == null
                ? getPeriodPoints(studentId, request.academicYear(), normalizedSemesterCode)
                : getPointsForTarget(studentId, targetPolicy, normalizedSemesterCode);
        BigDecimal targetPoints = targetPolicy == null
                ? request.targetPoints()
                : targetPolicy.getMinimumPoints();

        List<MileagePolicy> availablePolicies = findAvailablePolicies(
                request.academicYear(), normalizedSemesterCode);
        Map<Integer, MileagePolicy> policiesById = availablePolicies.stream()
                .collect(Collectors.toMap(
                        MileagePolicy::getMileagePolicyId,
                        Function.identity(),
                        (first, ignored) -> first));

        List<MileageSimulationResponse.PlannedActivity> plannedActivities = toPlannedActivities(
                request.plannedActivities(), policiesById);
        BigDecimal plannedPoints = plannedActivities.stream()
                .map(MileageSimulationResponse.PlannedActivity::plannedPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal projectedPoints = currentPoints.add(plannedPoints);
        BigDecimal shortagePoints = targetPoints.subtract(projectedPoints).max(BigDecimal.ZERO);

        return new MileageSimulationResponse.Result(
                new MileageSimulationResponse.Period(
                        request.academicYear(), normalizedSemesterCode),
                toTarget(targetPolicy, targetPoints),
                currentPoints,
                plannedPoints,
                projectedPoints,
                shortagePoints,
                shortagePoints.signum() == 0,
                plannedActivities);
    }

    private List<MileageSimulationResponse.PlannedActivity> toPlannedActivities(
            List<MileageSimulationRequest.PlannedActivity> requestedActivities,
            Map<Integer, MileagePolicy> policiesById
    ) {
        if (requestedActivities == null || requestedActivities.isEmpty()) {
            return List.of();
        }

        Set<Integer> requestedPolicyIds = new HashSet<>();
        return requestedActivities.stream()
                .map(requested -> {
                    if (!requestedPolicyIds.add(requested.mileagePolicyId())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_INPUT, "같은 활동 정책을 중복해서 선택할 수 없습니다.");
                    }

                    MileagePolicy policy = policiesById.get(requested.mileagePolicyId());
                    if (policy == null) {
                        throw new BusinessException(
                                ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                                "시뮬레이션에 사용할 수 없는 마일리지 정책입니다.");
                    }

                    BigDecimal plannedPoints = policy.getPoints()
                            .multiply(BigDecimal.valueOf(requested.quantity()));
                    if (policy.getMaximumPoints() != null) {
                        plannedPoints = plannedPoints.min(policy.getMaximumPoints());
                    }

                    MileageActivityType activityType = policy.getActivityType();
                    return new MileageSimulationResponse.PlannedActivity(
                            policy.getMileagePolicyId(),
                            activityType.getActivityCode(),
                            activityType.getActivityName(),
                            policy.getPoints(),
                            requested.quantity(),
                            plannedPoints);
                })
                .toList();
    }

    private List<MileagePolicy> findAvailablePolicies(
            Integer academicYear,
            String semesterCode
    ) {
        return mileagePolicyRepository.findSimulationPolicies(
                academicYear,
                semesterCodes(semesterCode),
                LocalDate.now());
    }

    private MileageSimulationResponse.TargetOption toTargetOption(
            Integer studentId,
            Integer academicYear,
            String semesterCode,
            BigDecimal periodPoints,
            MileageBenefitPolicy policy
    ) {
        BigDecimal currentPoints = ALL_SEMESTER_CODE.equalsIgnoreCase(policy.getSemesterCode())
                ? valueOrZero(mileageTransactionRepository
                .sumPostedPointsByStudentAndAcademicYear(studentId, academicYear))
                : periodPoints;
        BigDecimal shortagePoints = policy.getMinimumPoints()
                .subtract(currentPoints)
                .max(BigDecimal.ZERO);

        return new MileageSimulationResponse.TargetOption(
                policy.getBenefitPolicyId(),
                policy.getBenefitType(),
                policy.getBenefitName(),
                policy.getSemesterCode(),
                policy.getMinimumPoints(),
                currentPoints,
                shortagePoints);
    }

    private MileageSimulationResponse.ActivityOption toActivityOption(MileagePolicy policy) {
        MileageActivityType activityType = policy.getActivityType();
        return new MileageSimulationResponse.ActivityOption(
                policy.getMileagePolicyId(),
                activityType.getActivityCode(),
                activityType.getActivityName(),
                activityType.getCategoryCode(),
                activityType.getEarningRoute(),
                policy.getPoints(),
                policy.getMaximumPoints());
    }

    private MileageSimulationResponse.Target toTarget(
            MileageBenefitPolicy targetPolicy,
            BigDecimal targetPoints
    ) {
        if (targetPolicy == null) {
            return new MileageSimulationResponse.Target(
                    null, null, "직접 설정한 목표", targetPoints);
        }
        return new MileageSimulationResponse.Target(
                targetPolicy.getBenefitPolicyId(),
                targetPolicy.getBenefitType(),
                targetPolicy.getBenefitName(),
                targetPoints);
    }

    private MileageBenefitPolicy findTargetPolicy(MileageSimulationRequest request) {
        if (request.targetBenefitPolicyId() == null) {
            return null;
        }
        return benefitPolicyRepository
                .findByBenefitPolicyIdAndActiveTrue(request.targetBenefitPolicyId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                        "선택한 인증·장학 정책을 찾을 수 없습니다."));
    }

    private void validateTargetInput(MileageSimulationRequest request) {
        boolean hasPolicyTarget = request.targetBenefitPolicyId() != null;
        boolean hasDirectTarget = request.targetPoints() != null;
        if (hasPolicyTarget == hasDirectTarget) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "인증·장학 정책 또는 직접 목표 점수 중 하나만 선택해야 합니다.");
        }
    }

    private void validateTargetPeriod(
            MileageBenefitPolicy targetPolicy,
            Integer academicYear,
            String semesterCode
    ) {
        if (targetPolicy == null) {
            return;
        }

        boolean sameAcademicYear = Objects.equals(targetPolicy.getAcademicYear(), academicYear);
        boolean sameSemester = ALL_SEMESTER_CODE.equalsIgnoreCase(targetPolicy.getSemesterCode())
                || targetPolicy.getSemesterCode().equalsIgnoreCase(semesterCode);
        if (!sameAcademicYear || !sameSemester) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "선택한 인증·장학 정책이 조회 학기와 일치하지 않습니다.");
        }
    }

    private BigDecimal getPointsForTarget(
            Integer studentId,
            MileageBenefitPolicy targetPolicy,
            String semesterCode
    ) {
        if (ALL_SEMESTER_CODE.equalsIgnoreCase(targetPolicy.getSemesterCode())) {
            return valueOrZero(mileageTransactionRepository
                    .sumPostedPointsByStudentAndAcademicYear(
                            studentId, targetPolicy.getAcademicYear()));
        }
        return getPeriodPoints(studentId, targetPolicy.getAcademicYear(), semesterCode);
    }

    private BigDecimal getPeriodPoints(
            Integer studentId,
            Integer academicYear,
            String semesterCode
    ) {
        return valueOrZero(mileageTransactionRepository
                .sumPostedPointsByStudentAndPeriod(studentId, academicYear, semesterCode));
    }

    private Collection<String> semesterCodes(String semesterCode) {
        return List.of(semesterCode, ALL_SEMESTER_CODE);
    }

    private String validatePeriod(Integer academicYear, String semesterCode) {
        if (academicYear == null || academicYear < 2000 || academicYear > 9999
                || semesterCode == null || semesterCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기 정보가 올바르지 않습니다.");
        }
        if (ALL_SEMESTER_CODE.equalsIgnoreCase(semesterCode.trim())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기는 개별 학기로 지정해야 합니다.");
        }
        return semesterCode.trim();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
