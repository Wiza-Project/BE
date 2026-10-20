package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.response.StudentCounselingPublicResultResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingPublicResultService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증된 학생이 본인 예약에 속한 공개 상담 결과만 조회한다. 다른 학생의 결과, 미공개 초안과
 * 존재하지 않는 결과는 모두 동일하게 404(S011)로 응답해 결과 존재 여부를 노출하지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class StudentCounselingPublicResultController {

    private final CounselingPublicResultService counselingPublicResultService;

    /** 본인 공개 결과를 최신 공개순(publishedAt DESC, publicResultId DESC)으로 페이지 조회한다. */
    @GetMapping("/api/students/counseling-results")
    public ApiResponse<PageResponse<StudentCounselingPublicResultResponse>> getResults(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.getStudentResults(authUser.getId(), page, size));
    }

    /** 본인 예약에 속한 회기 하나의 공개 결과 상세를 조회한다. */
    @GetMapping("/api/students/counseling-sessions/{sessionId}/public-result")
    public ApiResponse<StudentCounselingPublicResultResponse> getResult(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingPublicResultService.getStudentResult(sessionId, authUser.getId()));
    }
}
