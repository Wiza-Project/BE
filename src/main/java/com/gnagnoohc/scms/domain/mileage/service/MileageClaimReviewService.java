package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimApproveRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewDetailResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewListResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewResultResponse;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.ExternalActivityClaimRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/** 교직원이 외부활동 마일리지 신청을 심사하고 승인 원장을 생성한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageClaimReviewService {

    private static final String REQUESTED_STATUS = "REQUESTED";
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String REJECTED_STATUS = "REJECTED";
    private static final String ALL_STATUS = "ALL";
    private static final Set<String> REVIEW_STATUSES = Set.of(
            REQUESTED_STATUS, APPROVED_STATUS, REJECTED_STATUS);

    private final ExternalActivityClaimRepository externalActivityClaimRepository;
    private final MileageTransactionRepository mileageTransactionRepository;

    /** 기본적으로 심사 대기 신청을 조회하고, 상태·학생·활동명으로 필터링한다. */
    public PageResponse<MileageClaimReviewListResponse> listClaims(
            String status,
            String keyword,
            Pageable pageable
    ) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        return PageResponse.from(
                externalActivityClaimRepository
                        .findForReview(normalizedStatus, normalizedKeyword, pageable)
                        .map(MileageClaimReviewListResponse::from));
    }

    /** 심사 화면에 필요한 신청·학생·활동·정책·증빙 정보를 조회한다. */
    public MileageClaimReviewDetailResponse getClaimDetail(Integer claimId) {
        ExternalActivityClaim claim = externalActivityClaimRepository
                .findReviewDetailById(requireClaimId(claimId))
                .orElseThrow(() -> claimNotFound(claimId));
        MileageTransaction transaction = mileageTransactionRepository
                .findBySourceExternalClaim_ExternalClaimId(claimId)
                .orElse(null);
        return MileageClaimReviewDetailResponse.from(claim, transaction);
    }

    /** 신청 행을 잠근 뒤 정책과 점수를 검증하고 승인 적립 원장을 생성한다. */
    @Transactional
    public MileageClaimReviewResultResponse approve(
            Integer claimId,
            Integer reviewerId,
            MileageClaimApproveRequest request
    ) {
        Integer validClaimId = requireClaimId(claimId);
        Integer validReviewerId = requireReviewerId(reviewerId);
        ExternalActivityClaim claim = getClaimForUpdate(validClaimId);

        validateApprovalTarget(claim);
        if (mileageTransactionRepository
                .findBySourceExternalClaim_ExternalClaimId(validClaimId)
                .isPresent()) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        BigDecimal points = resolveApprovedPoints(claim, request);
        Instant now = Instant.now();
        claim.approve(validReviewerId, now);

        MileageTransaction transaction = MileageTransaction.earnFromExternalClaim(
                claim, points, validReviewerId, now);
        MileageTransaction savedTransaction = mileageTransactionRepository.save(transaction);
        return MileageClaimReviewResultResponse.from(claim, savedTransaction);
    }

    /** 신청 행을 잠근 뒤 반려 상태와 사유를 저장한다. 반려 시 적립 원장은 만들지 않는다. */
    @Transactional
    public MileageClaimReviewResultResponse reject(
            Integer claimId,
            Integer reviewerId,
            String reason
    ) {
        Integer validClaimId = requireClaimId(claimId);
        Integer validReviewerId = requireReviewerId(reviewerId);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 사유는 필수입니다.");
        }
        ExternalActivityClaim claim = getClaimForUpdate(validClaimId);
        ensureRequested(claim);
        claim.reject(reason, validReviewerId, Instant.now());
        return MileageClaimReviewResultResponse.from(claim, null);
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
        if (!"ACTIVE".equalsIgnoreCase(policy.getPolicyStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "활성 상태의 마일리지 정책만 승인할 수 있습니다.");
        }

        LocalDate activityDate = claim.getActivityDate();
        if (activityDate == null
                || policy.getValidFrom() == null
                || activityDate.isBefore(policy.getValidFrom())
                || policy.getValidTo() != null && !activityDate.isBefore(policy.getValidTo())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "활동 일자가 마일리지 정책 적용 기간에 포함되지 않습니다.");
        }
    }

    private void ensureRequested(ExternalActivityClaim claim) {
        if (!REQUESTED_STATUS.equals(claim.getClaimStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }
    }

    private BigDecimal resolveApprovedPoints(
            ExternalActivityClaim claim,
            MileageClaimApproveRequest request
    ) {
        MileagePolicy policy = claim.getMileagePolicy();
        BigDecimal requestedPoints = claim.getRequestedPoints();
        BigDecimal approvedPoints = request == null ? null : request.approvedPoints();
        if (approvedPoints == null) {
            approvedPoints = requestedPoints != null ? requestedPoints : policy.getPoints();
        }

        if (approvedPoints == null || approvedPoints.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "승인할 마일리지 점수가 올바르지 않습니다.");
        }
        if (requestedPoints != null && approvedPoints.compareTo(requestedPoints) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "승인 점수는 신청 점수를 초과할 수 없습니다.");
        }
        if (policy.getMaximumPoints() != null
                && approvedPoints.compareTo(policy.getMaximumPoints()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "승인 점수가 정책의 최대 점수를 초과했습니다.");
        }
        return approvedPoints;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return REQUESTED_STATUS;
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
        return new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "마일리지 신청을 찾을 수 없습니다. claimId=" + claimId);
    }
}
