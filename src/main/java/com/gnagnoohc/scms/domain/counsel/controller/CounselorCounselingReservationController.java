package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.CounselorPendingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorReservationDecisionResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorReservationRejectRequest;
import com.gnagnoohc.scms.domain.counsel.service.CounselorReservationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상담사가 본인 일정에 걸린 DIRECT 예약을 승인·반려하는 요청을 받는다.
 * 상담사 ID는 요청 본문·경로가 아니라 인증된 사용자 정보에서 꺼내므로 다른 상담사로 가장할 수 없다.
 */
@RestController
@RequestMapping("/api/counselors/counseling-reservations")
@RequiredArgsConstructor
public class CounselorCounselingReservationController {

    private final CounselorReservationService counselorReservationService;

    /**
     * 로그인한 상담사의 승인 대기(REQUESTED) 예약 목록을 조회한다.
     */
    @GetMapping("/pending")
    public ApiResponse<PageResponse<CounselorPendingReservationResponse>> getPending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselorReservationService.getPending(authUser.getId(), page, size));
    }

    /**
     * 담당 예약 하나의 상세(신청 내용 포함)를 조회한다.
     */
    @GetMapping("/{reservationId}")
    public ApiResponse<CounselorReservationDetailResponse> getReservation(
            @PathVariable Integer reservationId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                counselorReservationService.getReservation(reservationId, authUser.getId())
        );
    }

    /**
     * REQUESTED 예약을 승인하고 최초 활성 배정을 함께 생성한다.
     */
    @PatchMapping("/{reservationId}/approve")
    public ApiResponse<CounselorReservationDecisionResponse> approve(
            @PathVariable Integer reservationId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                counselorReservationService.approve(reservationId, authUser.getId())
        );
    }

    /**
     * REQUESTED 예약을 거절한다. 배정은 생성하지 않는다.
     */
    @PatchMapping("/{reservationId}/reject")
    public ApiResponse<CounselorPendingReservationResponse> reject(
            @PathVariable Integer reservationId,
            @Valid @RequestBody CounselorReservationRejectRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                counselorReservationService.reject(reservationId, authUser.getId(), request.decisionReason())
        );
    }
}
