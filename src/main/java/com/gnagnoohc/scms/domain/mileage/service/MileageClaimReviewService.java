package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimCancelRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimRejectRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewDetailResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewListResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewResultResponse;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.event.ExternalActivityClaimDecisionEvent;
import com.gnagnoohc.scms.domain.mileage.repository.ExternalActivityClaimRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/** 교직원이 외부활동 마일리지 신청을 심사하고 승인 원장·역분개를 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageClaimReviewService {

    private static final String ALL_STATUS = "ALL";
    private static final Set<String> REVIEW_STATUSES = Set.of(
            ExternalActivityClaim.REQUESTED_STATUS,
            ExternalActivityClaim.APPROVED_STATUS,
            ExternalActivityClaim.REJECTED_STATUS,
            ExternalActivityClaim.CANCELLED_STATUS
    );

    private final ExternalActivityClaimRepository externalActivityClaimRepository;
    private final MileageTransactionRepository mileageTransactionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MileageAccrualCapService mileageAccrualCapService;
    private final MileageAcademicPeriodService mileageAcademicPeriodService;
    private final MileagePolicyValidator mileagePolicyValidator;

    /** 기본적으로 심사 대기 신청을 조회하고, 상태·학생명·학번·활동명으로 필터링한다. */
    public PageResponse<MileageClaimReviewListResponse> listClaims(
            String status,
            String keyword,
            Pageable pageable
    ) {
        return PageResponse.from(externalActivityClaimRepository
                .findForReview(normalizeStatus(status), normalizeKeyword(keyword), pageable)
                .map(MileageClaimReviewListResponse::from));
    }

    /** 심사 화면에 필요한 신청·학생·활동·정책·원장 정보를 조회한다. */
    public MileageClaimReviewDetailResponse getClaimDetail(Integer claimId) {
        Integer validClaimId = requireClaimId(claimId);
        ExternalActivityClaim claim = externalActivityClaimRepository
                .findReviewDetailById(validClaimId)
                .orElseThrow(() -> claimNotFound(validClaimId));
        MileageTransaction original = mileageTransactionRepository
                .findBySourceExternalClaim_ExternalClaimId(validClaimId)
                .orElse(null);
        MileageTransaction reversal = original == null ? null : mileageTransactionRepository
                .findByReversalOfTransaction_MileageTransactionId(original.getMileageTransactionId())
                .orElse(null);
        return MileageClaimReviewDetailResponse.from(claim, original, reversal);
    }

    /** 신청 행을 잠근 뒤 정책 점수로 승인 원장을 만들고 학생 알림 이벤트를 발행한다. */
    @Transactional
    public MileageClaimReviewResultResponse approve(Integer claimId, Integer reviewerId) {
        Integer validClaimId = requireClaimId(claimId);
        Integer validReviewerId = requireReviewerId(reviewerId);
        ExternalActivityClaim claim = getClaimForUpdate(validClaimId);

        validateApprovalTarget(claim);
        if (mileageTransactionRepository
                .findBySourceExternalClaim_ExternalClaimId(validClaimId)
                .isPresent()) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        Integer studentId = claim.getStudent().getUserId();
        BigDecimal grantablePoints = mileageAccrualCapService.computeGrantablePoints(
                studentId,
                claim.getMileagePolicy(),
                claim.getMileagePolicy().getPoints(),
                claim.getActivityDate());
        if (grantablePoints.signum() <= 0) {
            throw new BusinessException(ErrorCode.MILEAGE_CAP_EXCEEDED, "마일리지 적립 한도를 초과하여 승인할 수 없습니다.");
        }

        Instant now = Instant.now();
        claim.approve(validReviewerId, now);
        MileageTransaction transaction = mileageTransactionRepository.save(
                MileageTransaction.earnFromExternalClaim(claim, now, validReviewerId, grantablePoints));
        eventPublisher.publishEvent(new ExternalActivityClaimDecisionEvent(
                validClaimId,
                claim.getStudent().getUserId(),
                claim.getActivityName(),
                ExternalActivityClaim.APPROVED_STATUS,
                transaction.getPoints(),
                null));
        return MileageClaimReviewResultResponse.from(claim, transaction);
    }

    /** 신청 행을 잠근 뒤 반려 사유를 저장한다. 반려 시 원장은 만들지 않는다. */
    @Transactional
    public MileageClaimReviewResultResponse reject(
            Integer claimId,
            Integer reviewerId,
            MileageClaimRejectRequest request
    ) {
        Integer validClaimId = requireClaimId(claimId);
        Integer validReviewerId = requireReviewerId(reviewerId);
        String reason = request == null ? null : request.reason();
        ExternalActivityClaim claim = getClaimForUpdate(validClaimId);
        claim.reject(reason, validReviewerId, Instant.now());
        eventPublisher.publishEvent(new ExternalActivityClaimDecisionEvent(
                validClaimId,
                claim.getStudent().getUserId(),
                claim.getActivityName(),
                ExternalActivityClaim.REJECTED_STATUS,
                null,
                claim.getReviewReason()));
        return MileageClaimReviewResultResponse.from(claim, null);
    }

    /** 승인 원장은 수정하지 않고 음수 역분개 원장을 추가해 승인 적립을 취소한다. */
    @Transactional
    public MileageClaimReviewResultResponse cancel(
            Integer claimId,
            Integer reviewerId,
            MileageClaimCancelRequest request
    ) {
        Integer validClaimId = requireClaimId(claimId);
        Integer validReviewerId = requireReviewerId(reviewerId);
        String reason = request == null ? null : request.reason();
        ExternalActivityClaim claim = getClaimForUpdate(validClaimId);
        ensureApproved(claim);

        MileageTransaction original = mileageTransactionRepository
                .findBySourceExternalClaim_ExternalClaimId(validClaimId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "승인 적립 원장을 찾을 수 없습니다."));
        if (mileageTransactionRepository
                .findByReversalOfTransaction_MileageTransactionId(original.getMileageTransactionId())
                .isPresent()) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        Instant now = Instant.now();
        claim.cancel(reason, validReviewerId, now);
        MileageTransaction reversal = mileageTransactionRepository.save(
                MileageTransaction.reverseExternalClaim(original, validReviewerId, reason, now));
        eventPublisher.publishEvent(new ExternalActivityClaimDecisionEvent(
                validClaimId,
                claim.getStudent().getUserId(),
                claim.getActivityName(),
                ExternalActivityClaim.CANCELLED_STATUS,
                reversal.getPoints(),
                claim.getReviewReason()));
        return MileageClaimReviewResultResponse.from(claim, reversal);
    }

    private ExternalActivityClaim getClaimForUpdate(Integer claimId) {
        return externalActivityClaimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> claimNotFound(claimId));
    }

    private void validateApprovalTarget(ExternalActivityClaim claim) {
        ensureRequested(claim);

        if (claim.getFileGroup() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "증빙 파일이 없는 신청은 승인할 수 없습니다.");
        }

        MileageActivityType activityType = claim.getActivityType();
        if (activityType == null || !activityType.isActive()) {
            throw new BusinessException(ErrorCode.MILEAGE_ACTIVITY_TYPE_NOT_FOUND);
        }

        MileagePolicy policy = claim.getMileagePolicy();
        if (policy == null) {
            throw new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND);
        }
        if (policy.getActivityType() == null
                || !activityType.getActivityTypeId().equals(policy.getActivityType().getActivityTypeId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신청 활동과 마일리지 정책이 일치하지 않습니다.");
        }
        LocalDate activityDate = claim.getActivityDate();
        String semesterCode = mileageAcademicPeriodService.resolvePeriod(activityDate).semesterCode();
        if (!mileagePolicyValidator.isApplicable(policy, activityDate, semesterCode)) {
            throw new BusinessException(ErrorCode.MILEAGE_POLICY_NOT_FOUND, "승인에 사용할 활성 마일리지 정책이 없습니다.");
        }
    }

    private void ensureRequested(ExternalActivityClaim claim) {
        if (!ExternalActivityClaim.REQUESTED_STATUS.equals(claim.getClaimStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }
    }

    private void ensureApproved(ExternalActivityClaim claim) {
        if (!ExternalActivityClaim.APPROVED_STATUS.equals(claim.getClaimStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return ExternalActivityClaim.REQUESTED_STATUS;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (ALL_STATUS.equals(normalized)) {
            return null;
        }
        if (!REVIEW_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "마일리지 심사 상태값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private Integer requireClaimId(Integer claimId) {
        if (claimId == null || claimId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "마일리지 신청 번호가 올바르지 않습니다.");
        }
        return claimId;
    }

    private Integer requireReviewerId(Integer reviewerId) {
        if (reviewerId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "심사자 정보가 올바르지 않습니다.");
        }
        return reviewerId;
    }

    private BusinessException claimNotFound(Integer claimId) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "마일리지 신청을 찾을 수 없습니다. claimId=" + claimId);
    }
}
