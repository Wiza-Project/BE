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
import com.gnagnoohc.scms.global.error.DbConstraintViolationMatcher;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final String BENEFIT_APPLICATION_DUPLICATE_CONSTRAINT =
            "uq_mileage_benefit_application_policy_student";

    private final MileageBenefitPolicyRepository benefitPolicyRepository;
    private final MileageBenefitApplicationRepository benefitApplicationRepository;
    private final MileageTransactionRepository mileageTransactionRepository;
    private final AppUserRepository appUserRepository;
    private final MileageAcademicPeriodService mileageAcademicPeriodService;

    /** 선택 학기에 적용되는 활성 장학금 정책과 학생별 신청 가능 상태를 조회한다. */
    public List<MileageScholarshipResponse.ScholarshipItem> getScholarships(
            Integer studentId,
            String semesterCode
    ) {
        String selectedSemesterCode = validateSemester(semesterCode);
        List<MileageBenefitPolicy> policies = benefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndSemesterCodeInOrderByMinimumPointsAsc(
                        SCHOLARSHIP, List.of(selectedSemesterCode, ALL_SEMESTER_CODE));

        if (policies.isEmpty()) {
            return List.of();
        }

        Map<Integer, String> applicationStatuses = getApplicationStatuses(studentId, policies);
        Set<String> claimedGroupCodes = new HashSet<>(
                benefitApplicationRepository.findClaimedBenefitGroupCodes(studentId));
        MileageAcademicPeriodService.PeriodBounds periodBounds =
                mileageAcademicPeriodService.resolveCurrentPeriodBounds();
        BigDecimal semesterPoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(
                        studentId,
                        periodBounds.startAt(),
                        periodBounds.endAt(),
                        selectedSemesterCode));
        BigDecimal annualPoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudentBetween(
                        studentId, periodBounds.startAt(), periodBounds.endAt()));
        Instant now = Instant.now();

        return policies.stream()
                .map(policy -> toScholarshipItem(
                        policy,
                        resolvePoints(
                                studentId,
                                policy,
                                periodBounds,
                                semesterPoints,
                                annualPoints),
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
        MileageAcademicPeriodService.PeriodBounds currentPeriodBounds =
                mileageAcademicPeriodService.resolveCurrentPeriodBounds();
        BigDecimal currentPoints = calculatePoints(studentId, policy, currentPeriodBounds);
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

        // 같은 학생의 동시 신청 요청을 학생 행 락으로 직렬화한 뒤, 그 안에서 중복 신청 여부를 확인한다.
        AppUser student = appUserRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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

        MileageAcademicPeriodService.PeriodBounds currentPeriodBounds =
                mileageAcademicPeriodService.resolveCurrentPeriodBounds();
        BigDecimal currentPoints = calculatePoints(studentId, policy, currentPeriodBounds);
        if (!meetsPointRequirement(policy, currentPoints)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_MILEAGE);
        }

        MileageBenefitApplication application = MileageBenefitApplication.apply(
                policy, student, currentPoints, now);

        try {
            return toApplicationItem(benefitApplicationRepository.saveAndFlush(application));
        } catch (DataIntegrityViolationException exception) {
            // 선행 조회 이후 동시에 신청된 경우에도 유니크 제약을 학생용 충돌 응답으로 변환한다.
            if (!DbConstraintViolationMatcher.contains(exception, BENEFIT_APPLICATION_DUPLICATE_CONSTRAINT)) {
                throw exception;
            }
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
                        && !groupAlreadyClaimedByOther,
                policy.getCumulativeYears(),
                policy.getBenefitGroupCode());
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

    private BigDecimal calculatePoints(
            Integer studentId,
            MileageBenefitPolicy policy,
            MileageAcademicPeriodService.PeriodBounds currentPeriodBounds
    ) {
        if (policy.getCumulativeYears() != null && policy.getCumulativeYears() > 1) {
            LocalDate entryCycleStart = resolveEntryCycleStart(studentId);
            MileageAcademicPeriodService.PeriodBounds cumulativeBounds =
                    mileageAcademicPeriodService.resolveCycleBounds(
                            entryCycleStart, policy.getCumulativeYears());
            return valueOrZero(mileageTransactionRepository
                    .sumPostedPointsByStudentBetween(
                            studentId, cumulativeBounds.startAt(), cumulativeBounds.endAt()));
        }
        if (ALL_SEMESTER_CODE.equalsIgnoreCase(policy.getSemesterCode())) {
            return valueOrZero(mileageTransactionRepository
                    .sumPostedPointsByStudentBetween(
                            studentId, currentPeriodBounds.startAt(), currentPeriodBounds.endAt()));
        }
        return valueOrZero(mileageTransactionRepository
                .sumPostedPointsByStudentAndPeriod(
                        studentId,
                        currentPeriodBounds.startAt(),
                        currentPeriodBounds.endAt(),
                        policy.getSemesterCode()));
    }

    /**
     * 학생의 학번(university_no) 앞 4자리를 실제 입학년도로 파싱한다. 편입·휴학 등으로 학생마다
     * 입학년도가 다를 수 있어, 다년 누적 정책(cumulativeYears &gt; 1)은 학생 본인의 학번을 기준으로 삼는다.
     */
    private LocalDate resolveEntryCycleStart(Integer studentId) {
        AppUser student = appUserRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String universityNo = student.getUniversityNo();
        if (universityNo == null || universityNo.length() < 4) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학번에서 입학년도를 확인할 수 없습니다.");
        }
        try {
            int entryYear = Integer.parseInt(universityNo.substring(0, 4));
            return LocalDate.of(entryYear, 3, 2);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학번에서 입학년도를 확인할 수 없습니다.");
        }
    }

    private BigDecimal pointsFor(
            MileageBenefitPolicy policy,
            BigDecimal semesterPoints,
            BigDecimal annualPoints
    ) {
        return ALL_SEMESTER_CODE.equalsIgnoreCase(policy.getSemesterCode())
                ? annualPoints
                : semesterPoints;
    }

    /** 목록 조회는 학기/연간 점수를 사전 집계해 재사용하지만, 다년 누적 정책만은 별도로 계산한다. */
    private BigDecimal resolvePoints(
            Integer studentId,
            MileageBenefitPolicy policy,
            MileageAcademicPeriodService.PeriodBounds currentPeriodBounds,
            BigDecimal semesterPoints,
            BigDecimal annualPoints
    ) {
        if (policy.getCumulativeYears() != null && policy.getCumulativeYears() > 1) {
            try {
                return calculatePoints(studentId, policy, currentPeriodBounds);
            } catch (BusinessException exception) {
                if (ErrorCode.INVALID_INPUT.equals(exception.getErrorCode())) {
                    return BigDecimal.ZERO;
                }
                throw exception;
            }
        }
        return pointsFor(policy, semesterPoints, annualPoints);
    }

    private MileageScholarshipResponse.ApplicationItem toApplicationItem(
            MileageBenefitApplication application
    ) {
        MileageBenefitPolicy policy = application.getBenefitPolicy();
        return new MileageScholarshipResponse.ApplicationItem(
                application.getBenefitApplicationId(),
                policy.getBenefitPolicyId(),
                policy.getBenefitName(),
                policy.getSemesterCode(),
                policy.getBenefitAmount(),
                application.getPointsSnapshot(),
                application.getApplicationStatus(),
                application.getAppliedAt(),
                application.getProcessedAt(),
                application.getDecisionReason());
    }

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
