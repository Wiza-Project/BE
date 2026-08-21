package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingReservationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증된 학생이 본인 예약만 생성하고 조회하는 HTTP 경계다.
 */
@RestController
@RequestMapping("/api/students/counseling-reservations")
@RequiredArgsConstructor
public class CounselingReservationController {

    private final CounselingReservationService counselingReservationService;

    @PostMapping
    public ResponseEntity<ApiResponse<CounselingReservationResponse>> create(
            @Valid @RequestBody CounselingReservationRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        CounselingReservationResponse response = counselingReservationService.create(
                request,
                authUser.getId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ApiResponse<PageResponse<CounselingReservationResponse>> getReservations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingReservationService.getReservations(
                authUser.getId(),
                page,
                size
        ));
    }

    @GetMapping("/{reservationId}")
    public ApiResponse<CounselingReservationDetailResponse> getReservation(
            @PathVariable Integer reservationId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingReservationService.getReservation(
                reservationId,
                authUser.getId()
        ));
    }
}
