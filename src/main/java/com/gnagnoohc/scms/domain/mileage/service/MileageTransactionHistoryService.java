package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageTransactionHistoryResponse;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
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

    /**
     * 학생 본인의 확정 적립 내역을 10건 단위로 조회한다.
     * academicYear가 null이면 학기 필터 없이 전체 이력을, 지정되면 선택 학기(또는 ALL 정책) 거래만 반환한다.
     */
    public PageResponse<MileageTransactionHistoryResponse.ListItem> getEarnedTransactions(
            Integer studentId,
            Integer academicYear,
            String semesterCode,
            Pageable pageable
    ) {
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), PAGE_SIZE);
        MileageAcademicPeriodService.AcademicYearBounds academicYearBounds = academicYear == null
                ? null
                : mileageAcademicPeriodService.resolveAcademicYearBounds(academicYear);
        return PageResponse.from(
                mileageTransactionRepository
                        .findEarnedTransactions(
                                studentId,
                                academicYearBounds == null ? null : academicYearBounds.startAt(),
                                academicYearBounds == null ? null : academicYearBounds.endAt(),
                                semesterCode,
                                pageRequest)
                        .map(item -> new MileageTransactionHistoryResponse.ListItem(
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
        ExternalActivityClaim externalActivityClaim = resolveExternalActivityClaim(transaction);
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
                resolveSourceType(programApplication, externalActivityClaim),
                toPolicyDetail(policy),
                toProgramDetail(programApplication),
                toExternalActivityDetail(externalActivityClaim));
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

    private ExternalActivityClaim resolveExternalActivityClaim(MileageTransaction transaction) {
        if (transaction.getSourceExternalClaim() != null) {
            return transaction.getSourceExternalClaim();
        }
        return transaction.getReversalOfTransaction() == null
                ? null
                : transaction.getReversalOfTransaction().getSourceExternalClaim();
    }

    private String resolveSourceType(
            ProgramApplication programApplication,
            ExternalActivityClaim externalActivityClaim
    ) {
        if (programApplication != null) {
            return "EXTRACURRICULAR_PROGRAM";
        }
        if (externalActivityClaim != null) {
            return "EXTERNAL_ACTIVITY";
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

    private MileageTransactionHistoryResponse.ExternalActivityDetail toExternalActivityDetail(
            ExternalActivityClaim claim
    ) {
        if (claim == null) {
            return null;
        }

        MileageActivityType activityType = claim.getActivityType();
        return new MileageTransactionHistoryResponse.ExternalActivityDetail(
                claim.getExternalClaimId(),
                claim.getActivityName(),
                claim.getActivityDate(),
                claim.getRequestedPoints(),
                claim.getClaimStatus(),
                claim.getReviewReason(),
                activityType == null ? null : activityType.getActivityCode(),
                activityType == null ? null : activityType.getActivityName(),
                activityType == null ? null : activityType.getCategoryCode(),
                activityType == null ? null : activityType.getEarningRoute(),
                claim.getDetailData());
    }
}
