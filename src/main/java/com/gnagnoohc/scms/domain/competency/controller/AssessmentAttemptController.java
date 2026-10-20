package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentSubmitResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentResultService;
import com.gnagnoohc.scms.domain.competency.service.AssessmentSubmissionService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssessmentAttempt", description = "핵심역량 진단 응시(제출)")
@RestController
@RequestMapping("/api/students/assessment-attempts/{attemptId}")
@RequiredArgsConstructor
public class AssessmentAttemptController {

    private final AssessmentSubmissionService assessmentSubmissionService;
    private final AssessmentResultService assessmentResultService;

    @Operation(summary = "진단 제출", description = "미응답 문항이 없으면 제출을 확정하고, 같은 트랜잭션에서 역량별 환산점수를 산출합니다.")
    @PostMapping("/submit")
    public ApiResponse<AssessmentSubmitResponse> submit(
            @PathVariable Integer attemptId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentSubmissionService.submit(attemptId, authUser.getId()));
    }

    @Operation(summary = "진단 결과 조회", description = "역량별 환산점수(방사형 차트)와 전체 평균을 조회합니다. 백분위는 회차 집계가 완료된 경우에만 값이 채워집니다.")
    @GetMapping("/result")
    public ApiResponse<AssessmentResultResponse> getResult(
            @PathVariable Integer attemptId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentResultService.getResult(attemptId, authUser.getId()));
    }
}
