package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
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

    @Autowired
    public ProgramMileageAccrualService(
            ProgramApplicationMileageRepository programApplicationRepository,
            MileageTransactionRepository mileageTransactionRepository,
            MileagePolicyRepository mileagePolicyRepository) {
        this.programApplicationRepository = programApplicationRepository;
        this.mileageTransactionRepository = mileageTransactionRepository;
        this.mileagePolicyRepository = mileagePolicyRepository;
    }

    /** 기존 마일리지 단위 테스트와의 생성 호환을 유지한다. 운영 빈은 3개 의존성을 주입한다. */
    public ProgramMileageAccrualService(
            ProgramApplicationMileageRepository programApplicationRepository,
            MileageTransactionRepository mileageTransactionRepository) {
        this(programApplicationRepository, mileageTransactionRepository, null);
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
        Competency programCompetency = application.getProgram().getCompetency();
        MileagePolicy policy = resolvePolicy(application, programCompetency, completionDate, policyCache);
        validatePolicy(policy, programCompetency, completionDate);

        mileageTransactionRepository.save(
                MileageTransaction.earnFromProgramCompletion(application, policy, Instant.now()));
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
                                        Competency programCompetency,
                                        LocalDate completionDate,
                                        Map<PolicyLookupKey, MileagePolicy> policyCache) {
        MileagePolicy linkedPolicy = application.getProgram().getMileagePolicy();
        if (linkedPolicy != null
                && !ExtracurricularMileagePolicyDefinition.isExtracurricular(linkedPolicy.getActivityType())) {
            return linkedPolicy;
        }
        if (isUsablePolicy(linkedPolicy, programCompetency, completionDate)) {
            return linkedPolicy;
        }

        if (mileagePolicyRepository == null
                || programCompetency == null
                || programCompetency.getCompetencyId() == null) {
            return null;
        }

        PolicyLookupKey lookupKey = new PolicyLookupKey(
                programCompetency.getCompetencyId(), completionDate);
        if (policyCache != null && policyCache.containsKey(lookupKey)) {
            return policyCache.get(lookupKey);
        }

        MileagePolicy resolvedPolicy = mileagePolicyRepository
                .findActiveExtracurricularPoliciesByCompetencyOn(
                        programCompetency.getCompetencyId(),
                        ExtracurricularMileagePolicyDefinition.CATEGORY_CODE,
                        ExtracurricularMileagePolicyDefinition.EARNING_ROUTE,
                        completionDate)
                .stream()
                .filter(policy -> isUsablePolicy(policy, programCompetency, completionDate))
                .findFirst()
                .orElse(null);

        if (policyCache != null) {
            policyCache.put(lookupKey, resolvedPolicy);
        }
        return resolvedPolicy;
    }

    private record PolicyLookupKey(Integer competencyId, LocalDate completionDate) {
    }

    private boolean isUsablePolicy(MileagePolicy policy,
                                   Competency programCompetency,
                                   LocalDate completionDate) {
        return policy != null
                && "ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())
                && ExtracurricularMileagePolicyDefinition.isExtracurricular(policy.getActivityType())
                && policy.getActivityType().isActive()
                && sameCompetency(policy.getActivityType().getCompetency(), programCompetency)
                && policy.getPoints() != null
                && policy.getPoints().compareTo(ExtracurricularMileagePolicyDefinition.POINTS) == 0
                && policy.isApplicableOn(completionDate);
    }

    private boolean sameCompetency(Competency policyCompetency, Competency programCompetency) {
        return policyCompetency != null
                && programCompetency != null
                && Objects.equals(policyCompetency.getCompetencyId(), programCompetency.getCompetencyId());
    }

    private void validatePolicy(MileagePolicy policy,
                                Competency programCompetency,
                                LocalDate completionDate) {
        if (policy == null
                || !"ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())
                || policy.getActivityType() == null
                || !policy.getActivityType().isActive()
                || policy.getActivityType().getCompetency() == null
                || policy.getPoints() == null
                || policy.getPoints().compareTo(BigDecimal.ZERO) <= 0
                || !policy.isApplicableOn(completionDate)) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                    "이수일에 적용할 수 있는 마일리지 정책이 없습니다.");
        }

        if (ExtracurricularMileagePolicyDefinition.isExtracurricular(policy.getActivityType())
                && (policy.getPoints().compareTo(ExtracurricularMileagePolicyDefinition.POINTS) != 0
                || !sameCompetency(policy.getActivityType().getCompetency(), programCompetency))) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                    "프로그램 핵심역량에 맞는 5점 비교과 마일리지 정책이 없습니다.");
        }
    }
}
