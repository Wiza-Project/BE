package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.response.ProgramApplicationDecisionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramApplicationRejectRequestDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramApplicationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ProgramApplicationAdmin", description = "운영부서의 비교과 프로그램 참여 신청 승인/반려")
@RestController
@RequestMapping("/api/admin/programs/{programId}/applications")
@RequiredArgsConstructor
public class ProgramApplicationAdminController {

    private final ProgramApplicationService programApplicationService;

    @Operation(summary = "참여 신청 승인", description = "정원 이내인 경우에만 참여 신청을 승인합니다.")
    // HTTP POST 요청, 즉 "/api/admin/programs/{programId}/applications/{applicationId}/approve" 로 오는 요청을 처리한다.
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
}
