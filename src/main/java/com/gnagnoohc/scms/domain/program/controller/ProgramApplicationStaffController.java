package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramApplicationBulkApproveRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramApplicationBulkRejectRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationStaffListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationBulkDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramApplicationRejectRequestDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramApplicationService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ProgramApplicationStaff", description = "운영부서의 비교과 프로그램 참여 신청 승인/반려")
@RestController
@RequestMapping("/api/staff/programs/{programId}/applications")
@RequiredArgsConstructor
public class ProgramApplicationStaffController {

    private final ProgramApplicationService programApplicationService;

    @Operation(summary = "참여 신청 승인", description = "정원 이내인 경우에만 참여 신청을 승인합니다.")
    // HTTP POST 요청, 즉 "/api/staff/programs/{programId}/applications/{applicationId}/approve" 로 오는 요청을 처리한다.
    @PostMapping("/{applicationId}/approve")
    public ApiResponse<ProgramApplicationDecisionResponseDTO> approve(
            @PathVariable Integer programId,
            @PathVariable Integer applicationId,
            // 지금 로그인한 운영부서 담당자 정보. 처리자(processedBy)는 이 값으로만 결정한다.
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programApplicationService.approve(programId, applicationId, authUser.getId()));
    }

    @Operation(summary = "참여 신청 반려", description = "참여 신청을 반려합니다. 반려 사유(reason)는 필수입니다.")
    @PostMapping("/{applicationId}/reject")
    public ApiResponse<ProgramApplicationDecisionResponseDTO> reject(
            @PathVariable Integer programId,
            @PathVariable Integer applicationId,
            @Valid @RequestBody ProgramApplicationRejectRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programApplicationService.reject(
                programId, applicationId, request.reason(), authUser.getId()));
    }

    @Operation(summary = "참여 신청 목록 조회", description = "프로그램 하나의 전체 신청자를 상태 필터·이름/학번 키워드 검색과 함께 페이지 단위로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<ProgramApplicationStaffListItemResponseDTO>> list(
            @PathVariable Integer programId,
            // 신청 상태(APPLIED/WAITLISTED/APPROVED/REJECTED/CANCELLED)로 필터링. 생략하면 전체 상태를 조회한다.
            @RequestParam(required = false) String status,
            // 학생 이름/학번 부분 일치 검색. 생략하면 검색 없이 전체 신청자를 조회한다.
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programApplicationService.listByProgram(
                programId, status, keyword, pageable, authUser.getId(), authUser.getDepartmentCodeId()));
    }

    @Operation(summary = "참여 신청 일괄 승인", description = "선택한 여러 신청 건을 한 번에 승인합니다. 정원 초과 등으로 일부만 실패할 수 있습니다.")
    @PostMapping("/bulk-approve")
    public ApiResponse<ProgramApplicationBulkDecisionResponseDTO> bulkApprove(
            @PathVariable Integer programId,
            @Valid @RequestBody ProgramApplicationBulkApproveRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programApplicationService.bulkApprove(
                programId, request.applicationIds(), authUser.getId()));
    }

    @Operation(summary = "참여 신청 일괄 반려", description = "선택한 여러 신청 건을 한 번에 반려합니다. 반려 사유는 모든 건에 공통 적용됩니다.")
    @PostMapping("/bulk-reject")
    public ApiResponse<ProgramApplicationBulkDecisionResponseDTO> bulkReject(
            @PathVariable Integer programId,
            @Valid @RequestBody ProgramApplicationBulkRejectRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programApplicationService.bulkReject(
                programId, request.applicationIds(), request.reason(), authUser.getId()));
    }
}
