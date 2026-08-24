package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageTransactionHistoryResponse;
import com.gnagnoohc.scms.domain.mileage.service.MileageTransactionHistoryService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생의 전체 마일리지 적립 원장과 개별 적립 출처 상세 조회 API. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/mileage/transactions")
public class MileageTransactionController {

    private final MileageTransactionHistoryService mileageTransactionHistoryService;

    /** 확정된 마일리지 적립 내역을 최신순으로 10건씩 조회한다. */
    @GetMapping
    public ApiResponse<PageResponse<MileageTransactionHistoryResponse.ListItem>> getEarnedTransactions(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ApiResponse.ok(mileageTransactionHistoryService.getEarnedTransactions(
                authUser.getId(), pageable));
    }

    /** 적립 내역에서 어떤 비교과 프로그램·외부활동으로 적립되었는지 상세 조회한다. */
    @GetMapping("/{transactionId}")
    public ApiResponse<MileageTransactionHistoryResponse.Detail> getEarnedTransactionDetail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer transactionId
    ) {
        return ApiResponse.ok(mileageTransactionHistoryService.getEarnedTransactionDetail(
                authUser.getId(), transactionId));
    }
}
