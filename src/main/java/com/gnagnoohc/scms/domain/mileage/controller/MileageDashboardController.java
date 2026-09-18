package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageAcademicPeriodResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.MileageDashboardResponse;
import com.gnagnoohc.scms.domain.mileage.service.MileageAcademicPeriodService;
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

/** 학생이 자신의 마일리지 현황과 최근 활동을 조회하는 API를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/mileage")
public class MileageDashboardController {

    private final MileageDashboardService mileageDashboardService;
    private final MileageAcademicPeriodService mileageAcademicPeriodService;

    /** 오늘 날짜 기준 현재 학사 주기/학기를 판별한다. FE가 날짜로 직접 계산하던 것을 대체한다. */
    @GetMapping("/current-period")
    public ApiResponse<MileageAcademicPeriodResponse> getCurrentPeriod() {
        return ApiResponse.ok(mileageAcademicPeriodService.resolveCurrentPeriod());
    }

    /** 선택 학기의 점수·정책 진행도·분포·최근 내역을 한 번에 조회한다. */
    @GetMapping("/dashboard")
    public ApiResponse<MileageDashboardResponse> getDashboard(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam String semesterCode
    ) {
        return ApiResponse.ok(mileageDashboardService.getDashboard(
                authUser.getId(), semesterCode));
    }

    /** 적립 원장 탭의 미리보기로 최근 거래 5건을 조회한다. */
    @GetMapping("/transactions/recent")
    public ApiResponse<List<MileageDashboardResponse.TransactionSummary>> getRecentTransactions(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageDashboardService.getRecentTransactions(authUser.getId()));
    }
}
