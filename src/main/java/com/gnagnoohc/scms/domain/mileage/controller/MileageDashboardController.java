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
public class MileageDashboardController {

    private final MileageDashboardService mileageDashboardService;

    @GetMapping("/dashboard")
    public ApiResponse<MileageDashboardResponse> getDashboard(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Integer academicYear,
            @RequestParam String semesterCode
    ) {
        return ApiResponse.ok(mileageDashboardService.getDashboard(
                authUser.getId(), academicYear, semesterCode));
    }

    @GetMapping("/transactions/recent")
    public ApiResponse<List<MileageDashboardResponse.TransactionSummary>> getRecentTransactions(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(mileageDashboardService.getRecentTransactions(authUser.getId()));
    }

    @GetMapping("/external-activities/applications/recent")
    public ApiResponse<List<MileageDashboardResponse.ClaimSummary>> getRecentExternalActivityApplications(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(
                mileageDashboardService.getRecentExternalActivityApplications(authUser.getId()));
    }
}
