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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                git .findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        SCHOLARSHIP, academicYear, List.of(selectedSemesterCode, ALL_SEMESTER_CODE));

        if (policies.isEmpty()) {
            return List.of();
        }

        Map<Integer, String> applicationStatuses = getApplicationStatuses(studentId, policies);
        Set<String> claimedGroupCodes = new HashSet<>(
                benefitApplicationRepository.findClaimedBenefitGroupCodes(studentId));
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
                        resolvePoints(studentId, policy, semesterPoints, academicYearPoints),
                        applicationStatuses.get(policy.getBenefitPolicyId()),
                        isGroupAlreadyClaimedByOther(policy, claimedGroupCodes),
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
        boolean groupAlreadyClaimedByOther = policy.getBenefitGroupCode() != null
                && benefitApplicationRepository.existsByStudent_UserIdAndBenefitPolicy_BenefitGroupCode(
                        studentId, policy.getBenefitGroupCode());

        return toScholarshipItem(
                policy, currentPoints, applicationStatus, groupAlreadyClaimedByOther, Instant.now());
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

        if (policy.getBenefitGroupCode() != null
                && benefitApplicationRepository.existsByStudent_UserIdAndBenefitPolicy_BenefitGroupCode(
                        studentId, policy.getBenefitGroupCode())) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_ALREADY_CLAIMED, "이미 동일 유형의 장학금을 신청했습니다.");
        }

        Instant now = Instant.now();
        validateApplicationPeriod(policy, now);

        BigDecimal currentPoints = calculatePoints(studentId, policy);
        if (!meetsPointRequirement(policy, currentPoints)) {
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
            boolean groupAlreadyClaimedByOther,
            Instant now
    ) {
        BigDecimal shortagePoints = policy.getMinimumPoints()
                .subtract(currentPoints)
                .max(BigDecimal.ZERO);
        boolean applicationOpen = isApplicationOpen(policy, now);
        boolean enoughPoints = meetsPointRequirement(policy, currentPoints);
        String eligibilityStatus = resolveEligibilityStatus(
                policy, currentPoints, applicationStatus, groupAlreadyClaimedByOther, now);

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
                applicationStatus == null && enoughPoints && applicationOpen
                        && !groupAlreadyClaimedByOther);
    }

    private String resolveEligibilityStatus(
            MileageBenefitPolicy policy,
            BigDecimal currentPoints,
            String applicationStatus,
            boolean groupAlreadyClaimedByOther,
            Instant now
    ) {
        if (applicationStatus != null) {
            return applicationStatus;
        }
        if (groupAlreadyClaimedByOther) {
            return "GROUP_ALREADY_CLAIMED";
        }
        if (!meetsPointRequirement(policy, currentPoints)) {
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

    /** requiresExactPoints면 정확히 일치, 아니면 기존과 동일하게 최소 점수 이상인지 판정한다. */
    private boolean meetsPointRequirement(MileageBenefitPolicy policy, BigDecimal currentPoints) {
        int comparison = currentPoints.compareTo(policy.getMinimumPoints());
        return policy.isRequiresExactPoints() ? comparison == 0 : comparison >= 0;
    }

    private boolean isGroupAlreadyClaimedByOther(
            MileageBenefitPolicy policy,
            Set<String> claimedGroupCodes
    ) {
        return policy.getBenefitGroupCode() != null
                && claimedGroupCodes.contains(policy.getBenefitGroupCode());
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
        if (policy.getCumulativeYears() != null && policy.getCumulativeYears() > 1) {
            int entryYear = resolveEntryYear(studentId);
            int endYear = entryYear + policy.getCumulativeYears() - 1;
            return valueOrZero(mileageTransactionRepository
                    .sumPostedPointsByStudentAndAcademicYearRange(studentId, entryYear, endYear));
        }
        if (ALL_SEMESTER_CODE.equalsIgnoreCase(policy.getSemesterCode())) {
            return valueOrZero(mileageTransactionRepository
                    .sumPostedPointsByStudentAndAcademicYear(studentId, policy.getAcademicYear()));
        }
        return valueOrZero(mileageTransactionRepository
                .sumPostedPointsByStudentAndPeriod(
                        studentId, policy.getAcademicYear(), policy.getSemesterCode()));
    }

    /**
     * 학생의 학번(university_no) 앞 4자리를 실제 입학년도로 파싱한다. 편입·휴학 등으로 학생마다
     * 입학년도가 다를 수 있어, 4년 누적 정책(cumulativeYears &gt; 1)의 academicYear에서
     * 역산하지 않고 학생 본인의 학번을 기준으로 삼는다.
     */
    private int resolveEntryYear(Integer studentId) {
        AppUser student = appUserRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String universityNo = student.getUniversityNo();
        if (universityNo == null || universityNo.length() < 4) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학번에서 입학년도를 확인할 수 없습니다.");
        }
        try {
            return Integer.parseInt(universityNo.substring(0, 4));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학번에서 입학년도를 확인할 수 없습니다.");
        }
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

    /** 목록 조회는 학기/연간 점수를 사전 집계해 재사용하지만, 다년 누적 정책만은 별도로 계산한다. */
    private BigDecimal resolvePoints(
            Integer studentId,
            MileageBenefitPolicy policy,
            BigDecimal semesterPoints,
            BigDecimal academicYearPoints
    ) {
        if (policy.getCumulativeYears() != null && policy.getCumulativeYears() > 1) {
            return calculatePoints(studentId, policy);
        }
        return pointsFor(policy, semesterPoints, academicYearPoints);
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
