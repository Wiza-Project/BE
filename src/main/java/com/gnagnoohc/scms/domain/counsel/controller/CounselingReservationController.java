package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingReservationCancelRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingReservationRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingReservationScheduleChangeRequest;
import com.gnagnoohc.scms.domain.counsel.service.CounselingReservationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증된 학생이 본인 상담 예약을 생성·조회·취소·일정변경하는 HTTP 경계다.
 * 모든 메서드가 studentId를 요청 파라미터로 받지 않고 {@link AuthUser}(JWT를 검증한
 * JwtAuthenticationFilter가 SecurityContext에 심어둔 로그인 사용자 정보)에서 꺼내 쓴다.
 * 그래야 클라이언트가 studentId 값을 조작해 다른 학생의 예약을 건드리는 경로를 원천 차단할 수 있다.
 */
@RestController
@RequestMapping("/api/students/counseling-reservations")
@RequiredArgsConstructor
public class CounselingReservationController {

    private final CounselingReservationService counselingReservationService;

    /**
     * 새 상담 예약을 신청한다.
     * 신청 가능 여부(유형 활성화, 일정 마감·정원, 본인 시간 중복 등)는 서비스 계층에서 검증하며,
     * 컨트롤러는 인증 정보를 꺼내 서비스로 전달하는 역할만 한다.
     */
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

    /**
     * 로그인한 학생 본인의 예약 목록을 최신순으로 페이지 조회한다.
     * page, size를 생략하면 0페이지·20건이 기본값이다.
     */
    @GetMapping
    public ApiResponse<PageResponse<CounselingReservationResponse>> getReservations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingReservationService.getReservations(
                authUser.getId(),
                page,
                size
        ));
    }

    /**
     * 학생 본인이 예약을 취소한다.
     * 예약 리소스 전체를 새로 대체하는 게 아니라 "취소 상태로 바꾸고 사유를 남기는" 부분 수정이라
     * PUT이 아닌 PATCH를 쓴다. 취소 가능한 상태·기한인지는 서비스와 엔티티가 검증하므로
     * 컨트롤러는 통과시키기만 한다.
     */
    @PatchMapping("/{reservationId}/cancel")
    public ApiResponse<CounselingReservationResponse> cancel(
            @PathVariable Integer reservationId,
            @Valid @RequestBody CounselingReservationCancelRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingReservationService.cancel(
                reservationId,
                authUser.getId(),
                request
        ));
    }

    /**
     * 학생 본인이 예약의 상담 일정만 다른 회차로 바꾼다(예약을 새로 만드는 게 아니다).
     * 아직 상담사 승인 전(REQUESTED) 예약만 가능하고, 새 일정도 신규 신청과 같은 기준
     * (유형 일치, 마감 전, 정원 여유, 담당 상담사 활성 상태, 본인 시간 중복 없음)으로 다시 검증된다.
     */
    @PatchMapping("/{reservationId}/schedule")
    public ApiResponse<CounselingReservationResponse> changeSchedule(
            @PathVariable Integer reservationId,
            @Valid @RequestBody CounselingReservationScheduleChangeRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingReservationService.changeSchedule(
                reservationId,
                authUser.getId(),
                request
        ));
    }

    /**
     * 예약 상세를 조회한다.
     * 다른 학생의 예약이거나 존재하지 않는 id일 때 동일하게 404를 반환해,
     * 예약 id의 존재 여부 자체가 응답 차이로 노출되지 않게 한다.
     */
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
