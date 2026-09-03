package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.mileage.repository.ProgramApplicationMileageRepository;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 비교과 이수 완료 건에 연결된 정책 점수만 마일리지 원장에 적립한다. */
@Slf4j
@Service
public class ProgramMileageAccrualService {

    private static final int BATCH_SIZE = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String COMPLETED = "COMPLETED";

    private final ProgramApplicationMileageRepository programApplicationRepository;
    private final MileageTransactionRepository mileageTransactionRepository;
    private final MileagePolicyRepository mileagePolicyRepository;
    private final MileageAccrualCapService mileageAccrualCapService;

    @Autowired
    public ProgramMileageAccrualService(
            ProgramApplicationMileageRepository programApplicationRepository,
            MileageTransactionRepository mileageTransactionRepository,
            MileagePolicyRepository mileagePolicyRepository,
            MileageAccrualCapService mileageAccrualCapService) {
        this.programApplicationRepository = programApplicationRepository;
        this.mileageTransactionRepository = mileageTransactionRepository;
        this.mileagePolicyRepository = mileagePolicyRepository;
        this.mileageAccrualCapService = mileageAccrualCapService;
    }

    /** 기존 마일리지 단위 테스트와의 생성 호환을 유지한다. 운영 빈은 4개 의존성을 주입한다. */
    public ProgramMileageAccrualService(
            ProgramApplicationMileageRepository programApplicationRepository,
            MileageTransactionRepository mileageTransactionRepository) {
        this(programApplicationRepository, mileageTransactionRepository, null, null);
    }

    /** 특정 비교과 신청이 이수 완료된 경우 고정 정책 점수로 한 번만 적립한다. */
    @Transactional
    public boolean accrueProgramCompletion(Integer applicationId) {
        return accrueProgramCompletion(applicationId, null);
    }

    private boolean accrueProgramCompletion(Integer applicationId,
                                            Map<PolicyLookupKey, MileagePolicy> policyCache) {
        if (applicationId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비교과 신청 번호가 올바르지 않습니다.");
        }

        if (mileageTransactionRepository
                .findBySourceProgramApplication_ApplicationId(applicationId)
                .isPresent()) {
            return false;
        }

        ProgramApplication application = programApplicationRepository
                .findCompletedForAccrual(applicationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_COMPLETED,
                        "이수 완료된 비교과 프로그램 신청을 찾을 수 없습니다."));

        // 신청 건을 잠근 뒤 다시 확인해 동시 실행된 적립 요청을 멱등 처리한다.
        if (mileageTransactionRepository
                .findBySourceProgramApplication_ApplicationId(applicationId)
                .isPresent()) {
            return false;
        }

        LocalDate completionDate = application.getCompletedAt() == null
                ? LocalDate.now(BUSINESS_ZONE)
                : application.getCompletedAt().atZone(BUSINESS_ZONE).toLocalDate();
        String programTypeCode = resolveProgramTypeCode(application);
        MileagePolicy policy = resolvePolicy(application, programTypeCode, completionDate, policyCache);
        validatePolicy(policy, programTypeCode, completionDate);

        Integer studentId = application.getStudent().getUserId();
        BigDecimal grantablePoints = mileageAccrualCapService == null
                ? policy.getPoints()
                : mileageAccrualCapService.computeGrantablePoints(
                        studentId, policy, policy.getPoints(), completionDate);
        if (grantablePoints.signum() <= 0) {
            log.info("마일리지 적립 한도 초과로 비교과 이수 적립을 건너뜁니다. applicationId={}, studentId={}",
                    applicationId, studentId);
            return false;
        }

        mileageTransactionRepository.save(
                MileageTransaction.earnFromProgramCompletion(application, policy, grantablePoints, Instant.now()));
        return true;
    }

    /** 스케줄러가 아직 적립되지 않은 이수 완료 신청을 배치로 처리한다. */
    @Transactional
    public int accruePendingProgramCompletions() {
        var applications = programApplicationRepository.findCompletedWithoutMileage(
                PageRequest.of(0, BATCH_SIZE));
        Map<PolicyLookupKey, MileagePolicy> policyCache = new HashMap<>();
        int accruedCount = 0;

        for (ProgramApplication application : applications) {
            try {
                if (accrueProgramCompletion(application.getApplicationId(), policyCache)) {
                    accruedCount++;
                }
            } catch (RuntimeException exception) {
                log.warn("비교과 마일리지 자동 적립을 건너뜁니다. applicationId={}, reason={}",
                        application.getApplicationId(), exception.getMessage());
            }
        }
        return accruedCount;
    }

    private MileagePolicy resolvePolicy(ProgramApplication application,
                                        String programTypeCode,
                                        LocalDate completionDate,
                                        Map<PolicyLookupKey, MileagePolicy> policyCache) {
        MileagePolicy linkedPolicy = application.getProgram().getMileagePolicy();
        // 프로그램 이수 적립은 비교과 프로그램 유형별 정책만 사용한다.
        // 비교과가 아닌 연결 정책은 그대로 사용하지 않고, 아래의 유효한 정책 조회로 넘긴다.
        if (isUsablePolicy(linkedPolicy, programTypeCode, completionDate)) {
            return linkedPolicy;
        }

        if (mileagePolicyRepository == null
                || programTypeCode == null
                || programTypeCode.isBlank()) {
            return null;
        }

        PolicyLookupKey lookupKey = new PolicyLookupKey(
                programTypeCode, completionDate);
        if (policyCache != null && policyCache.containsKey(lookupKey)) {
            return policyCache.get(lookupKey);
        }

        MileagePolicy resolvedPolicy = mileagePolicyRepository
                .findActiveExtracurricularPoliciesByProgramTypeOn(
                        programTypeCode,
                        ExtracurricularMileagePolicyDefinition.CATEGORY_CODE,
                        ExtracurricularMileagePolicyDefinition.EARNING_ROUTE,
                        completionDate)
                .stream()
                .filter(policy -> isUsablePolicy(policy, programTypeCode, completionDate))
                .findFirst()
                .orElse(null);

        if (policyCache != null) {
            policyCache.put(lookupKey, resolvedPolicy);
        }
        return resolvedPolicy;
    }

    private record PolicyLookupKey(String programTypeCode, LocalDate completionDate) {
    }

    private boolean isUsablePolicy(MileagePolicy policy,
                                   String programTypeCode,
                                   LocalDate completionDate) {
        return policy != null
                && "ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())
                && ExtracurricularMileagePolicyDefinition.isExtracurricular(policy.getActivityType())
                && policy.getActivityType().isActive()
                && sameProgramType(policy.getActivityType(), programTypeCode)
                && policy.getPoints() != null
                && policy.getPoints().compareTo(BigDecimal.ZERO) > 0
                && policy.isApplicableOn(completionDate);
    }

    private boolean sameProgramType(MileageActivityType activityType, String programTypeCode) {
        return activityType != null
                && ExtracurricularMileagePolicyDefinition.isProgramTypePolicy(activityType)
                && activityType.getProgramTypeCode() != null
                && programTypeCode != null
                && Objects.equals(activityType.getProgramTypeCode().getCode(), programTypeCode);
    }

    private void validatePolicy(MileagePolicy policy,
                                String programTypeCode,
                                LocalDate completionDate) {
        if (policy == null
                || !"ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())
                || policy.getActivityType() == null
                || !policy.getActivityType().isActive()
                || policy.getPoints() == null
                || policy.getPoints().compareTo(BigDecimal.ZERO) <= 0
                || !policy.isApplicableOn(completionDate)) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                    "이수일에 적용할 수 있는 마일리지 정책이 없습니다.");
        }

        if (ExtracurricularMileagePolicyDefinition.isExtracurricular(policy.getActivityType())
                && !sameProgramType(policy.getActivityType(), programTypeCode)) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                    "프로그램 유형에 맞는 비교과 마일리지 정책이 없습니다.");
        }
    }

    private String resolveProgramTypeCode(ProgramApplication application) {
        if (application.getProgram().getProgramTypeCode() == null) {
            return null;
        }
        return application.getProgram().getProgramTypeCode().getCode();
    }
}
