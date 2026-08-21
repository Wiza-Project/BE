package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorScheduleResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingScheduleService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상담사 일정 요청을 HTTP 입력으로 받아 서비스에 전달한다.
 * 상담사 ID는 요청 본문이 아니라 인증된 사용자 정보에서 꺼내므로 다른 상담사로 가장할 수 없다.
 */
@RestController
@RequestMapping("/api/counselors/schedules")
@RequiredArgsConstructor
public class CounselingScheduleController {

    private final CounselingScheduleService counselingScheduleService;

    /**
     * 상담사가 본인 소유 일정과 수정 가능 여부를 한 번에 확인한다.
     */
    @GetMapping
    public ApiResponse<List<CounselorScheduleResponse>> getCounselorSchedules(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingScheduleService.getCounselorSchedules(authUser.getId()));
    }

    /**
     * 로그인한 상담사의 예약 가능한 새 일정을 연다.
     */
    @PostMapping
    public ApiResponse<CounselingScheduleResponse> create(
            @Valid @RequestBody CounselingScheduleRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingScheduleService.create(request, authUser.getId()));
    }

    /**
     * 일정의 일부가 아니라 수정 요청에 담긴 값 전체로 기존 일정을 교체한다.
     */
    @PutMapping("/{scheduleId}")
    public ApiResponse<CounselingScheduleResponse> update(
            @PathVariable Integer scheduleId,
            @Valid @RequestBody CounselingScheduleRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                counselingScheduleService.update(scheduleId, request, authUser.getId())
        );
    }

    /**
     * 기존 예약은 유지하면서 새 예약만 받지 않도록 일정을 마감한다.
     */
    @PatchMapping("/{scheduleId}/close")
    public ApiResponse<CounselingScheduleResponse> close(
            @PathVariable Integer scheduleId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingScheduleService.close(scheduleId, authUser.getId()));
    }
}
