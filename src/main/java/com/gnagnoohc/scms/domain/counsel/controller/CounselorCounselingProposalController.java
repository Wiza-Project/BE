package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingProposalCreateRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingProposalEligibleResultResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingProposalResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingProposalService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ST200 only 상담사가 최신 17점 이상 스트레스 결과를 조회하고 상담을 제안하는 API 경계다.
 * 역할·범위 최종 판정은 CounselingProposalService(CounselManagementAccessPolicy)가 하므로
 * 이 컨트롤러는 인증된 상담사 ID만 서비스로 전달한다.
 * page/size는 이 컨트롤러에서 직접 범위를 확인해 잘못된 값이면 400 C001로 응답한다.
 */
@RestController
@RequestMapping("/api/counselors/counseling-proposals")
@RequiredArgsConstructor
public class CounselorCounselingProposalController {

    private final CounselingProposalService counselingProposalService;

    @GetMapping("/eligible-results")
    public ApiResponse<PageResponse<CounselingProposalEligibleResultResponse>> getEligibleResults(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        validatePage(page, size);
        return ApiResponse.ok(counselingProposalService.getEligibleResults(authUser.getId(), page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CounselingProposalResponse> create(
            @Valid @RequestBody CounselingProposalCreateRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingProposalService.create(request, authUser.getId()));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
