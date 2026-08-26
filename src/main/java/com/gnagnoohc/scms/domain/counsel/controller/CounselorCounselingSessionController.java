package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionCancelRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionCompleteRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionCreateRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingSessionService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 상담사가 자신의 배정에 딸린 회기를 조회·생성·완료·취소한다.
 * 상담사 ID는 인증된 사용자 정보에서만 꺼내므로 URL·요청 본문으로 다른 상담사를 가장할 수 없다.
 */
@RestController
@RequestMapping("/api/counselors")
@RequiredArgsConstructor
public class CounselorCounselingSessionController {

    private final CounselingSessionService counselingSessionService;

    @GetMapping("/counseling-sessions")
    public ApiResponse<PageResponse<CounselingSessionResponse>> getSessions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sessionStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                counselingSessionService.getSessions(authUser.getId(), page, size, sessionStatus, from, to)
        );
    }

    @GetMapping("/counseling-sessions/{sessionId}")
    public ApiResponse<CounselingSessionResponse> getSession(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingSessionService.getSession(sessionId, authUser.getId()));
    }

    @PostMapping("/counseling-assignments/{assignmentId}/sessions")
    public ApiResponse<CounselingSessionResponse> createFollowUp(
            @PathVariable Integer assignmentId,
            @Valid @RequestBody CounselingSessionCreateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingSessionService.createFollowUp(
                assignmentId, request.startsAt(), request.endsAt(), authUser.getId()
        ));
    }

    @PatchMapping("/counseling-sessions/{sessionId}/complete")
    public ApiResponse<CounselingSessionResponse> complete(
            @PathVariable Integer sessionId,
            @Valid @RequestBody CounselingSessionCompleteRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingSessionService.complete(
                sessionId, request.attendanceStatus(), request.nextSessionAt(), authUser.getId()
        ));
    }

    @PatchMapping("/counseling-sessions/{sessionId}/cancel")
    public ApiResponse<CounselingSessionResponse> cancel(
            @PathVariable Integer sessionId,
            @Valid @RequestBody CounselingSessionCancelRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingSessionService.cancel(
                sessionId, request.cancellationReason(), authUser.getId()
        ));
    }
}
