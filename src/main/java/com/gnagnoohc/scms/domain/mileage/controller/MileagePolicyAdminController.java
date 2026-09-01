package com.gnagnoohc.scms.domain.mileage.controller;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyUpdateRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileagePolicyResponseDTO;
import com.gnagnoohc.scms.domain.mileage.service.MileagePolicyService;
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

/** 활동 유형별 마일리지 점수 기준(정책) 등록/조회/수정. 교직원(STAFF)만 사용할 수 있다. */
@Tag(name = "MileagePolicyStaff", description = "교직원 전용 마일리지 활동별 점수 기준(정책) 관리")
@RestController
@RequestMapping("/api/staff/mileage/policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
public class MileagePolicyAdminController {

    private final MileagePolicyService mileagePolicyService;

    @Operation(summary = "마일리지 정책 등록", description = "활동 유형별 마일리지 지급 점수와 적용 기간을 등록합니다. 버전 번호는 서버가 자동으로 채번합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MileagePolicyResponseDTO> register(
            @Valid @RequestBody MileagePolicyRegisterRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(mileagePolicyService.register(request, authUser.getId()));
    }

    @Operation(summary = "마일리지 정책 목록 조회", description = "활동 유형/학년도/학기/상태로 필터링하여 정책 목록을 페이지 단위로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<MileagePolicyResponseDTO>> list(
            @RequestParam(required = false) Integer activityTypeId,
            @RequestParam(required = false) Integer academicYear,
            @RequestParam(required = false) String semesterCode,
            @RequestParam(required = false) String policyStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(mileagePolicyService.list(activityTypeId, academicYear, semesterCode, policyStatus, pageable));
    }

    @Operation(summary = "마일리지 정책 상세 조회")
    @GetMapping("/{mileagePolicyId}")
    public ApiResponse<MileagePolicyResponseDTO> getDetail(@PathVariable Integer mileagePolicyId) {
        return ApiResponse.ok(mileagePolicyService.getDetail(mileagePolicyId));
    }

    @Operation(summary = "마일리지 정책 수정", description = "점수/최대점수/적용기간/중복적립규칙/상태를 부분 수정합니다. 활동 유형·학년도·학기·버전은 변경할 수 없습니다.")
    @PatchMapping("/{mileagePolicyId}")
    public ApiResponse<MileagePolicyResponseDTO> update(
            @PathVariable Integer mileagePolicyId,
            @Valid @RequestBody MileagePolicyUpdateRequestDTO request) {
        return ApiResponse.ok(mileagePolicyService.update(mileagePolicyId, request));
    }
}
