package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageScholarshipResponse;
import com.gnagnoohc.scms.domain.mileage.service.MileageScholarshipService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 학생의 장학금 조회·신청·본인 신청 이력 조회 API. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/mileage/scholarships")
public class MileageScholarshipController {

    private final MileageScholarshipService mileageScholarshipService;

    /** 선택 학기에 적용되는 장학금 정책과 신청 가능 상태를 조회한다. */
    @GetMapping
    public ApiResponse<List<MileageScholarshipResponse.ScholarshipItem>> getScholarships(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam String semesterCode
    ) {
        return ApiResponse.ok(mileageScholarshipService.getScholarships(
                authUser.getId(), semesterCode));
    }

    /** 장학금 정책 상세와 본인의 신청 상태를 조회한다. */
    @GetMapping("/{benefitPolicyId}")
    public ApiResponse<MileageScholarshipResponse.ScholarshipItem> getScholarship(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer benefitPolicyId
    ) {
        return ApiResponse.ok(mileageScholarshipService.getScholarship(
                authUser.getId(), benefitPolicyId));
    }

    /** 학생 본인의 장학금 신청을 접수한다. */
    @PostMapping("/{benefitPolicyId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MileageScholarshipResponse.ApplicationItem> apply(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer benefitPolicyId
    ) {
        return ApiResponse.ok(mileageScholarshipService.apply(
                authUser.getId(), benefitPolicyId));
    }

    /** 학생 본인의 장학금 신청 이력을 최신순으로 조회한다. */
    @GetMapping("/applications")
    public ApiResponse<PageResponse<MileageScholarshipResponse.ApplicationItem>> getApplicationHistory(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10, sort = "appliedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.ok(mileageScholarshipService.getApplicationHistory(
                authUser.getId(), pageable));
    }
}
