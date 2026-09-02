package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageBenefitPolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageBenefitPolicyUpdateRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageBenefitPolicyResponseDTO;
import com.gnagnoohc.scms.domain.mileage.service.MileageBenefitPolicyService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증·장학 등 마일리지 혜택 정책 등록/조회/수정. 교직원(STAFF)만 사용할 수 있다. */
@Tag(name = "MileageBenefitPolicyStaff", description = "교직원 전용 마일리지 인증·장학 혜택 정책 관리")
@RestController
@RequestMapping("/api/staff/mileage/benefit-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
public class MileageBenefitPolicyAdminController {

    private final MileageBenefitPolicyService mileageBenefitPolicyService;

    @Operation(summary = "혜택 정책 등록", description = "학기별 인증·장학 혜택의 최소 점수와 신청 기간을 등록합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MileageBenefitPolicyResponseDTO> register(
            @Valid @RequestBody MileageBenefitPolicyRegisterRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(mileageBenefitPolicyService.register(request, authUser.getId()));
    }

    @Operation(summary = "혜택 정책 목록 조회", description = "혜택유형/학년도/학기/활성여부로 필터링하여 정책 목록을 페이지 단위로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<MileageBenefitPolicyResponseDTO>> list(
            @RequestParam(required = false) String benefitType,
            @RequestParam(required = false) Integer academicYear,
            @RequestParam(required = false) String semesterCode,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(
                mileageBenefitPolicyService.list(benefitType, academicYear, semesterCode, active, pageable));
    }

    @Operation(summary = "혜택 정책 상세 조회")
    @GetMapping("/{benefitPolicyId}")
    public ApiResponse<MileageBenefitPolicyResponseDTO> getDetail(@PathVariable Integer benefitPolicyId) {
        return ApiResponse.ok(mileageBenefitPolicyService.getDetail(benefitPolicyId));
    }

    @Operation(summary = "혜택 정책 수정", description = "목표점수/금액/신청기간/활성여부를 부분 수정합니다. 혜택유형·학년도·학기는 변경할 수 없습니다.")
    @PatchMapping("/{benefitPolicyId}")
    public ApiResponse<MileageBenefitPolicyResponseDTO> update(
            @PathVariable Integer benefitPolicyId,
            @Valid @RequestBody MileageBenefitPolicyUpdateRequestDTO request) {
        return ApiResponse.ok(mileageBenefitPolicyService.update(benefitPolicyId, request));
    }
}
