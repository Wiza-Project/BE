package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimCancelRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimRejectRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewDetailResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewListResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewResultResponse;
import com.gnagnoohc.scms.domain.mileage.service.MileageClaimReviewService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 교직원이 외부활동 마일리지 신청을 조회하고 승인·반려·취소하는 API다. */
@Tag(name = "MileageClaimReview", description = "외부활동 마일리지 심사")
@RestController
@RequestMapping("/api/staff/mileage/claims")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
public class MileageClaimReviewController {

    private final MileageClaimReviewService mileageClaimReviewService;

    @Operation(summary = "마일리지 심사 신청 목록 조회", description = "기본적으로 심사 대기 신청을 최신순으로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<MileageClaimReviewListResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.ok(mileageClaimReviewService.listClaims(status, keyword, pageable));
    }

    @Operation(summary = "마일리지 심사 신청 상세 조회")
    @GetMapping("/{claimId}")
    public ApiResponse<MileageClaimReviewDetailResponse> detail(@PathVariable Integer claimId) {
        return ApiResponse.ok(mileageClaimReviewService.getClaimDetail(claimId));
    }

    @Operation(summary = "마일리지 신청 승인", description = "증빙과 활성 정책을 검증한 뒤 정책 등록 점수로 적립 원장을 생성합니다.")
    @PostMapping("/{claimId}/approve")
    public ApiResponse<MileageClaimReviewResultResponse> approve(
            @PathVariable Integer claimId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageClaimReviewService.approve(claimId, authUser.getId()));
    }

    @Operation(summary = "마일리지 신청 반려", description = "반려 사유를 저장하고 신청을 반려 상태로 변경합니다.")
    @PostMapping("/{claimId}/reject")
    public ApiResponse<MileageClaimReviewResultResponse> reject(
            @PathVariable Integer claimId,
            @Valid @RequestBody MileageClaimRejectRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageClaimReviewService.reject(claimId, authUser.getId(), request));
    }

    @Operation(summary = "승인된 마일리지 적립 취소", description = "기존 원장은 보존하고 반대 부호의 역분개 원장을 생성합니다.")
    @PostMapping("/{claimId}/cancel")
    public ApiResponse<MileageClaimReviewResultResponse> cancel(
            @PathVariable Integer claimId,
            @Valid @RequestBody MileageClaimCancelRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageClaimReviewService.cancel(claimId, authUser.getId(), request));
    }
}
