package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingProposalAcceptRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingProposalResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingProposalService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 본인의 상담 제안 목록 조회, 수락, 거절을 받는다. 요청의 학생 ID로 소유권을 결정하지 않고
 * 인증된 사용자 ID만 서비스로 전달하므로 다른 학생의 제안에 접근할 수 없다.
 */
@RestController
@RequestMapping("/api/students/counseling-proposals")
@RequiredArgsConstructor
public class StudentCounselingProposalController {

    private final CounselingProposalService counselingProposalService;

    @GetMapping
    public ApiResponse<PageResponse<CounselingProposalResponse>> getMyProposals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return ApiResponse.ok(counselingProposalService.getStudentProposals(authUser.getId(), page, size));
    }

    @PatchMapping("/{proposalId}/accept")
    public ApiResponse<CounselingProposalResponse> accept(
            @PathVariable Integer proposalId,
            @Valid @RequestBody CounselingProposalAcceptRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        validateProposalId(proposalId);
        return ApiResponse.ok(
                counselingProposalService.accept(proposalId, authUser.getId(), request)
        );
    }

    /** 요청 본문이 없다. 거절 사유를 받거나 저장하지 않는다. */
    @PatchMapping("/{proposalId}/reject")
    public ApiResponse<CounselingProposalResponse> reject(
            @PathVariable Integer proposalId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        validateProposalId(proposalId);
        return ApiResponse.ok(
                counselingProposalService.reject(proposalId, authUser.getId())
        );
    }

    private void validateProposalId(Integer proposalId) {
        if (proposalId == null || proposalId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
