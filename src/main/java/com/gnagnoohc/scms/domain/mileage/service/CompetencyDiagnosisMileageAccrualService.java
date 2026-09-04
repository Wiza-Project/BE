package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.AssessmentAttemptMileageRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageActivityTypeRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/** 역량진단(사전/사후) 제출 완료 건에 연결된 정책 점수만 마일리지 원장에 적립한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetencyDiagnosisMileageAccrualService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final AssessmentAttemptMileageRepository assessmentAttemptMileageRepository;
    private final MileageTransactionRepository mileageTransactionRepository;
    private final MileageActivityTypeRepository mileageActivityTypeRepository;
    private final MileagePolicyRepository mileagePolicyRepository;
    private final MileageAccrualCapService mileageAccrualCapService;
    private final MileageAcademicPeriodService mileageAcademicPeriodService;
    private final AppUserRepository appUserRepository;
    private final AssessmentCompletionMileageAccrualWriter assessmentCompletionMileageAccrualWriter;

    /** 특정 응시 회차가 제출 완료된 경우 고정 정책 점수로 한 번만 적립한다. */
    @Transactional
    public boolean accrueAssessmentCompletion(Integer attemptId) {
        if (attemptId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "역량진단 응시 번호가 올바르지 않습니다.");
        }

        if (mileageTransactionRepository
                .findBySourceAssessmentAttempt_AttemptId(attemptId)
                .isPresent()) {
            return false;
        }

        AssessmentAttempt attempt = assessmentAttemptMileageRepository
                .findWithStudentByAttemptId(attemptId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT, "역량진단 응시 정보를 찾을 수 없습니다."));

        // 동시 제출 요청이 같은 학생에 대해 동시에 적립되지 않도록 학생 행을 잠근 뒤 중복 여부를 다시 확인한다.
        Integer studentId = attempt.getStudent().getUserId();
        appUserRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (mileageTransactionRepository
                .findBySourceAssessmentAttempt_AttemptId(attemptId)
                .isPresent()) {
            return false;
        }

        LocalDate submittedDate = attempt.getSubmittedAt() == null
                ? LocalDate.now(BUSINESS_ZONE)
                : attempt.getSubmittedAt().atZone(BUSINESS_ZONE).toLocalDate();

        MileagePolicy policy = resolvePolicy(submittedDate);
        if (policy == null) {
            log.warn("제출일에 적용 가능한 역량진단 마일리지 정책이 없어 적립을 건너뜁니다. attemptId={}, submittedDate={}",
                    attemptId, submittedDate);
            return false;
        }
        validatePolicy(policy, submittedDate);
        if (policy.getActivityType().getCompetency() == null) {
            log.warn("정책에 연결된 핵심역량이 없어 역량진단 적립을 건너뜁니다. attemptId={}, policyId={}",
                    attemptId, policy.getMileagePolicyId());
            return false;
        }

        BigDecimal grantablePoints = mileageAccrualCapService.computeGrantablePoints(
                studentId, policy, policy.getPoints(), submittedDate);
        if (grantablePoints.signum() <= 0) {
            log.info("마일리지 적립 한도 초과로 역량진단 완료 적립을 건너뜁니다. attemptId={}, studentId={}",
                    attemptId, studentId);
            return false;
        }

        return assessmentCompletionMileageAccrualWriter.insert(attempt, policy, grantablePoints, attemptId);
    }

    private MileagePolicy resolvePolicy(LocalDate submittedDate) {
        MileageActivityType activityType = mileageActivityTypeRepository
                .findByActivityCode(CompetencyDiagnosisMileagePolicyDefinition.ACTIVITY_CODE)
                .orElse(null);
        if (activityType == null) {
            return null;
        }

        String semesterCode = mileageAcademicPeriodService.resolvePeriod(submittedDate).semesterCode();
        return mileagePolicyRepository
                .findActivePoliciesByActivityTypeOn(activityType.getActivityTypeId(), submittedDate, semesterCode)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void validatePolicy(MileagePolicy policy, LocalDate submittedDate) {
        if (policy == null
                || !"ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())
                || policy.getActivityType() == null
                || !policy.getActivityType().isActive()
                || policy.getPoints() == null
                || policy.getPoints().compareTo(BigDecimal.ZERO) <= 0
                || !policy.isApplicableOn(submittedDate)) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                    "제출일에 적용할 수 있는 역량진단 마일리지 정책이 없습니다.");
        }
    }
}
