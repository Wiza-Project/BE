package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.mileage.DTO.MileageTransactionHistoryResponse;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드와 분리된 학생 마일리지 적립 원장 조회를 담당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageTransactionHistoryService {

    private static final int PAGE_SIZE = 10;
    private static final String EARN = "EARN";
    private static final String POSTED = "POSTED";

    private final MileageTransactionRepository mileageTransactionRepository;
    private final MileageAcademicPeriodService mileageAcademicPeriodService;
    private final MileageSemesterCodeValidator mileageSemesterCodeValidator;

    /**
     * 학생 본인의 확정 적립 내역을 10건 단위로 조회한다.
     * semesterCode가 null이면 학기 필터 없이 전체 이력을, 지정되면 현재 주기의 해당 학기 거래만 반환한다.
     */
    public PageResponse<MileageTransactionHistoryResponse.ListItem> getEarnedTransactions(
            Integer studentId,
            String semesterCode,
            Pageable pageable
    ) {
        String normalizedSemesterCode = mileageSemesterCodeValidator.normalizeSemesterCodeIfPresent(semesterCode);
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), PAGE_SIZE);
        Page<MileageTransactionRepository.TransactionHistoryProjection> page;
        if (normalizedSemesterCode == null) {
            page = mileageTransactionRepository.findAllEarnedTransactions(studentId, pageRequest);
        } else {
            MileageAcademicPeriodService.PeriodBounds periodBounds =
                    mileageAcademicPeriodService.resolveCurrentPeriodBounds();
            page = mileageTransactionRepository.findEarnedTransactions(
                    studentId,
                    periodBounds.startAt(),
                    periodBounds.endAt(),
                    normalizedSemesterCode,
                    pageRequest);
        }
        return PageResponse.from(
                page.map(item -> new MileageTransactionHistoryResponse.ListItem(
                                item.getTransactionId(),
                                item.getActivityName(),
                                item.getSourceType(),
                                item.getTransactionType(),
                                item.getPoints(),
                                item.getTransactionStatus(),
                                item.getOccurredAt())));
    }

    /** 학생 본인의 확정 적립 내역만 상세 조회한다. */
    public MileageTransactionHistoryResponse.Detail getEarnedTransactionDetail(
            Integer studentId,
            Integer transactionId
    ) {
        MileageTransaction transaction = mileageTransactionRepository
                .findByMileageTransactionIdAndStudent_UserIdAndTransactionTypeAndTransactionStatus(
                        transactionId, studentId, EARN, POSTED)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "마일리지 적립 내역을 찾을 수 없습니다."));

        ProgramApplication programApplication = resolveProgramApplication(transaction);
        MileagePolicy policy = resolveMileagePolicy(transaction);

        return new MileageTransactionHistoryResponse.Detail(
                transaction.getMileageTransactionId(),
                transaction.getTransactionType(),
                transaction.getPoints(),
                transaction.getTransactionStatus(),
                transaction.getTransactionReason(),
                transaction.getPostedAt() != null
                        ? transaction.getPostedAt()
                        : transaction.getCreatedAt(),
                resolveSourceType(transaction, programApplication),
                toPolicyDetail(policy),
                toProgramDetail(programApplication));
    }

    private MileagePolicy resolveMileagePolicy(MileageTransaction transaction) {
        if (transaction.getMileagePolicy() != null) {
            return transaction.getMileagePolicy();
        }
        return transaction.getReversalOfTransaction() == null
                ? null
                : transaction.getReversalOfTransaction().getMileagePolicy();
    }

    private ProgramApplication resolveProgramApplication(MileageTransaction transaction) {
        if (transaction.getSourceProgramApplication() != null) {
            return transaction.getSourceProgramApplication();
        }
        return transaction.getReversalOfTransaction() == null
                ? null
                : transaction.getReversalOfTransaction().getSourceProgramApplication();
    }

    private AssessmentAttempt resolveAssessmentAttempt(MileageTransaction transaction) {
        if (transaction.getSourceAssessmentAttempt() != null) {
            return transaction.getSourceAssessmentAttempt();
        }
        return transaction.getReversalOfTransaction() == null
                ? null
                : transaction.getReversalOfTransaction().getSourceAssessmentAttempt();
    }

    private String resolveSourceType(MileageTransaction transaction, ProgramApplication programApplication) {
        if (programApplication != null) {
            return "EXTRACURRICULAR_PROGRAM";
        }
        if (resolveAssessmentAttempt(transaction) != null) {
            return "COMPETENCY_DIAGNOSIS";
        }
        return "OTHER";
    }

    private MileageTransactionHistoryResponse.PolicyDetail toPolicyDetail(MileagePolicy policy) {
        if (policy == null) {
            return null;
        }

        MileageActivityType activityType = policy.getActivityType();
        return new MileageTransactionHistoryResponse.PolicyDetail(
                policy.getMileagePolicyId(),
                activityType == null ? null : activityType.getActivityCode(),
                activityType == null ? null : activityType.getActivityName(),
                activityType == null ? null : activityType.getCategoryCode(),
                activityType == null ? null : activityType.getEarningRoute(),
                policy.getSemesterCode(),
                policy.getPoints());
    }

    private MileageTransactionHistoryResponse.ProgramDetail toProgramDetail(
            ProgramApplication application
    ) {
        if (application == null || application.getProgram() == null) {
            return null;
        }

        ExtracurricularProgram program = application.getProgram();
        return new MileageTransactionHistoryResponse.ProgramDetail(
                application.getApplicationId(),
                program.getProgramId(),
                program.getProgramName(),
                application.getCompletionStatus(),
                application.getCertificateNo(),
                application.getCertificateIssuedAt());
    }
}
