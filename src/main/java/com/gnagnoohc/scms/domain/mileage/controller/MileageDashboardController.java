package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageDashboardResponse;
import com.gnagnoohc.scms.domain.mileage.service.MileageDashboardService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/mileage")
/** 학생이 자신의 마일리지 현황과 최근 활동을 조회하는 API를 제공한다. */
public class MileageDashboardController {

    private final MileageDashboardService mileageDashboardService;

    /** 선택 학기의 점수·정책 진행도·분포·최근 내역을 한 번에 조회한다. */
    @GetMapping("/dashboard")
    public ApiResponse<MileageDashboardResponse> getDashboard(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Integer academicYear,
            @RequestParam String semesterCode
    ) {
        return ApiResponse.ok(mileageDashboardService.getDashboard(
                authUser.getId(), academicYear, semesterCode));
    }

    /** 적립 원장 탭의 미리보기로 최근 거래 5건을 조회한다. */
    @GetMapping("/transactions/recent")
    public ApiResponse<List<MileageDashboardResponse.TransactionSummary>> getRecentTransactions(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageDashboardService.getRecentTransactions(authUser.getId()));
    }

    /** 외부활동 신청 상태를 빠르게 확인할 수 있도록 최근 신청 5건을 조회한다. */
    @GetMapping("/external-activities/applications/recent")
    public ApiResponse<List<MileageDashboardResponse.ClaimSummary>> getRecentExternalActivityApplications(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                mileageDashboardService.getRecentExternalActivityApplications(authUser.getId()));
    }
}
