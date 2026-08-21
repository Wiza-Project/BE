package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.mileage.repository.ProgramApplicationMileageRepository;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 비교과 이수 완료 건에 연결된 정책 점수만 마일리지 원장에 적립한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramMileageAccrualService {

    private static final int BATCH_SIZE = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String COMPLETED = "COMPLETED";

    private final ProgramApplicationMileageRepository programApplicationRepository;
    private final MileageTransactionRepository mileageTransactionRepository;

    /** 특정 비교과 신청이 이수 완료된 경우 고정 정책 점수로 한 번만 적립한다. */
    @Transactional
    public boolean accrueProgramCompletion(Integer applicationId) {
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

        MileagePolicy policy = application.getProgram().getMileagePolicy();
        LocalDate completionDate = application.getCompletedAt() == null
                ? LocalDate.now(BUSINESS_ZONE)
                : application.getCompletedAt().atZone(BUSINESS_ZONE).toLocalDate();
        validatePolicy(policy, completionDate);

        mileageTransactionRepository.save(
                MileageTransaction.earnFromProgramCompletion(application, Instant.now()));
        return true;
    }

    /** 스케줄러가 아직 적립되지 않은 이수 완료 신청을 배치로 처리한다. */
    @Transactional
    public int accruePendingProgramCompletions() {
        var applications = programApplicationRepository.findCompletedWithoutMileage(
                PageRequest.of(0, BATCH_SIZE));
        int accruedCount = 0;

        for (ProgramApplication application : applications) {
            try {
                if (accrueProgramCompletion(application.getApplicationId())) {
                    accruedCount++;
                }
            } catch (RuntimeException exception) {
                log.warn("비교과 마일리지 자동 적립을 건너뜁니다. applicationId={}, reason={}",
                        application.getApplicationId(), exception.getMessage());
            }
        }
        return accruedCount;
    }

    private void validatePolicy(MileagePolicy policy, LocalDate completionDate) {
        if (policy == null
                || !"ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())
                || policy.getActivityType() == null
                || !policy.getActivityType().isActive()
                || policy.getActivityType().getCompetency() == null
                || policy.getPoints() == null
                || policy.getPoints().compareTo(BigDecimal.ZERO) <= 0
                || policy.getValidFrom() == null
                || completionDate.isBefore(policy.getValidFrom())
                || (policy.getValidTo() != null && completionDate.isAfter(policy.getValidTo()))) {
            throw new BusinessException(
                    ErrorCode.MILEAGE_POLICY_NOT_FOUND,
                    "이수일에 적용할 수 있는 마일리지 정책이 없습니다.");
        }
    }
}
