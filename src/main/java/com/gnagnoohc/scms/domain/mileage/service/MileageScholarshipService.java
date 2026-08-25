package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageScholarshipResponse;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitApplication;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitApplicationRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 학생의 장학금 정책 조회, 신청, 본인 신청 이력을 담당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageScholarshipService {

    private static final String SCHOLARSHIP = "SCHOLARSHIP";
    private static final String ALL_SEMESTER_CODE = "ALL";
    private static final int APPLICATION_HISTORY_PAGE_SIZE = 10;

    private final MileageBenefitPolicyRepository benefitPolicyRepository;
    private final MileageBenefitApplicationRepository benefitApplicationRepository;
    private final MileageTransactionRepository mileageTransactionRepository;
    private final AppUserRepository appUserRepository;

    /** 선택 학기에 적용되는 활성 장학금 정책과 학생별 신청 가능 상태를 조회한다. */
    public List<MileageScholarshipResponse.ScholarshipItem> getScholarships(
            Integer studentId,
            Integer academicYear,
            String semesterCode
    ) {
        String selectedSemesterCode = validatePeriod(academicYear, semesterCode);
        List<MileageBenefitPolicy> policies = benefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        SCHOLARSHIP, academicYear, List.of(selectedSemesterCode, ALL_SEMESTER_CODE));

        if (policies.isEmpty()) {
            return List.of();
        }

        Map<Integer, String> applicationStatuses = getApplicationStatuses(studentId, policies);
        BigDecimal semesterPoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(
                        studentId, academicYear, selectedSemesterCode));
        BigDecimal academicYearPoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudentAndAcademicYear(
                        studentId, academicYear));
        Instant now = Instant.now();

        return policies.stream()
                .map(policy -> toScholarshipItem(
                        policy,
                        pointsFor(policy, semesterPoints, academicYearPoints),
                        applicationStatuses.get(policy.getBenefitPolicyId()),
                        now))
                .toList();
    }

    /** 학생이 선택한 장학금 정책의 상세와 본인 신청 상태를 조회한다. */
    public MileageScholarshipResponse.ScholarshipItem getScholarship(
            Integer studentId,
            Integer benefitPolicyId
    ) {
        MileageBenefitPolicy policy = findActiveScholarshipPolicy(benefitPolicyId);
        String applicationStatus = benefitApplicationRepository
                .findByBenefitPolicy_BenefitPolicyIdAndStudent_UserId(benefitPolicyId, studentId)
                .map(MileageBenefitApplication::getApplicationStatus)
                .orElse(null);
        BigDecimal currentPoints = calculatePoints(studentId, policy);

        return toScholarshipItem(policy, currentPoints, applicationStatus, Instant.now());
    }

    /** 학생이 기준을 충족한 장학금을 신청하고 신청 시점 점수를 보존한다. */
    @Transactional
    public MileageScholarshipResponse.ApplicationItem apply(
            Integer studentId,
            Integer benefitPolicyId
    ) {
        MileageBenefitPolicy policy = findActiveScholarshipPolicy(benefitPolicyId);

        if (benefitApplicationRepository
                .findByBenefitPolicy_BenefitPolicyIdAndStudent_UserId(benefitPolicyId, studentId)
                .isPresent()) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_ALREADY_CLAIMED, "이미 해당 장학금을 신청했습니다.");
        }

        Instant now = Instant.now();
        validateApplicationPeriod(policy, now);

        BigDecimal currentPoints = calculatePoints(studentId, policy);
        if (currentPoints.compareTo(policy.getMinimumPoints()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_MILEAGE);
        }

        AppUser student = appUserRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        MileageBenefitApplication application = MileageBenefitApplication.apply(
                policy, student, currentPoints, now);

        try {
            return toApplicationItem(benefitApplicationRepository.save(application));
        } catch (DataIntegrityViolationException exception) {
            // 선행 조회 이후 동시에 신청된 경우에도 유니크 제약을 학생용 충돌 응답으로 변환한다.
            throw new BusinessException(
                    ErrorCode.MILEAGE_ALREADY_CLAIMED, "이미 해당 장학금을 신청했습니다.");
        }
    }

    /** 학생 본인의 장학금 신청 이력을 최신 신청순으로 조회한다. */
    public PageResponse<MileageScholarshipResponse.ApplicationItem> getApplicationHistory(
            Integer studentId,
            Pageable pageable
    ) {
        PageRequest pageRequest = PageRequest.of(
                pageable.getPageNumber(), APPLICATION_HISTORY_PAGE_SIZE, pageable.getSort());
        return PageResponse.from(
                benefitApplicationRepository
                        .findAllByStudent_UserIdAndBenefitPolicy_BenefitTypeOrderByAppliedAtDesc(
                                studentId, SCHOLARSHIP, pageRequest)
                        .map(this::toApplicationItem));
    }

    private MileageBenefitPolicy findActiveScholarshipPolicy(Integer benefitPolicyId) {
        return benefitPolicyRepository
                .findByBenefitPolicyIdAndBenefitTypeAndActiveTrue(benefitPolicyId, SCHOLARSHIP)
                .orElseThrow(() -> new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND));
    }

    private Map<Integer, String> getApplicationStatuses(
            Integer studentId,
            List<MileageBenefitPolicy> policies
    ) {
        return benefitApplicationRepository.findApplicationStatuses(
                        studentId,
                        policies.stream()
                                .map(MileageBenefitPolicy::getBenefitPolicyId)
                                .toList())
                .stream()
                .collect(Collectors.toMap(
                        MileageBenefitApplicationRepository.ApplicationStatusProjection::getBenefitPolicyId,
                        MileageBenefitApplicationRepository.ApplicationStatusProjection::getApplicationStatus,
                        (first, ignored) -> first));
    }

    private MileageScholarshipResponse.ScholarshipItem toScholarshipItem(
            MileageBenefitPolicy policy,
            BigDecimal currentPoints,
            String applicationStatus,
            Instant now
    ) {
        BigDecimal shortagePoints = policy.getMinimumPoints()
                .subtract(currentPoints)
                .max(BigDecimal.ZERO);
        boolean applicationOpen = isApplicationOpen(policy, now);
        boolean enoughPoints = shortagePoints.signum() == 0;
        String eligibilityStatus = resolveEligibilityStatus(
                policy, currentPoints, applicationStatus, now);

        return new MileageScholarshipResponse.ScholarshipItem(
                policy.getBenefitPolicyId(),
                policy.getBenefitType(),
                policy.getBenefitName(),
                policy.getAcademicYear(),
                policy.getSemesterCode(),
                policy.getMinimumPoints(),
                currentPoints,
                shortagePoints,
                policy.getBenefitAmount(),
                policy.getCriteriaData(),
                policy.getApplicationStartsAt(),
                policy.getApplicationEndsAt(),
                eligibilityStatus,
                applicationStatus,
                applicationStatus == null && enoughPoints && applicationOpen);
    }

    private String resolveEligibilityStatus(
            MileageBenefitPolicy policy,
            BigDecimal currentPoints,
            String applicationStatus,
            Instant now
    ) {
        if (applicationStatus != null) {
            return applicationStatus;
        }
        if (currentPoints.compareTo(policy.getMinimumPoints()) < 0) {
            return "INSUFFICIENT_POINTS";
        }
        if (policy.getApplicationStartsAt() != null
                && now.isBefore(policy.getApplicationStartsAt())) {
            return "APPLICATION_NOT_OPEN";
        }
        if (policy.getApplicationEndsAt() != null
                && !now.isBefore(policy.getApplicationEndsAt())) {
            return "APPLICATION_CLOSED";
        }
        return "ELIGIBLE";
    }

    private boolean isApplicationOpen(MileageBenefitPolicy policy, Instant now) {
        return (policy.getApplicationStartsAt() == null
                || !now.isBefore(policy.getApplicationStartsAt()))
                && (policy.getApplicationEndsAt() == null
                || now.isBefore(policy.getApplicationEndsAt()));
    }

    private void validateApplicationPeriod(MileageBenefitPolicy policy, Instant now) {
        if (!isApplicationOpen(policy, now)) {
            throw new BusinessException(ErrorCode.APPLICATION_PERIOD_CLOSED);
        }
    }

    private BigDecimal calculatePoints(Integer studentId, MileageBenefitPolicy policy) {
        if (ALL_SEMESTER_CODE.equalsIgnoreCase(policy.getSemesterCode())) {
            return valueOrZero(mileageTransactionRepository
                    .sumPostedPointsByStudentAndAcademicYear(studentId, policy.getAcademicYear()));
        }
        return valueOrZero(mileageTransactionRepository
                .sumPostedPointsByStudentAndPeriod(
                        studentId, policy.getAcademicYear(), policy.getSemesterCode()));
    }

    private BigDecimal pointsFor(
            MileageBenefitPolicy policy,
            BigDecimal semesterPoints,
            BigDecimal academicYearPoints
    ) {
        return ALL_SEMESTER_CODE.equalsIgnoreCase(policy.getSemesterCode())
                ? academicYearPoints
                : semesterPoints;
    }

    private MileageScholarshipResponse.ApplicationItem toApplicationItem(
            MileageBenefitApplication application
    ) {
        MileageBenefitPolicy policy = application.getBenefitPolicy();
        return new MileageScholarshipResponse.ApplicationItem(
                application.getBenefitApplicationId(),
                policy.getBenefitPolicyId(),
                policy.getBenefitName(),
                policy.getAcademicYear(),
                policy.getSemesterCode(),
                policy.getBenefitAmount(),
                application.getPointsSnapshot(),
                application.getApplicationStatus(),
                application.getAppliedAt(),
                application.getProcessedAt(),
                application.getDecisionReason());
    }

    private String validatePeriod(Integer academicYear, String semesterCode) {
        if (academicYear == null || academicYear < 2000 || academicYear > 9999
                || semesterCode == null || semesterCode.isBlank()) {
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
