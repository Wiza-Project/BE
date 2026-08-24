package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentResponseSaveRequest;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentResponseSaveResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentResumeResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentResponseService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssessmentResponse", description = "핵심역량 진단 응답 저장(중도저장)·이어하기")
@RestController
@RequestMapping("/api/students/assessment-attempts/{attemptId}/responses")
@RequiredArgsConstructor
public class AssessmentResponseController {

    private final AssessmentResponseService assessmentResponseService;

    @Operation(summary = "이어하기 조회", description = "해당 attempt의 회차 문항 전체와 기존 저장된 응답값(없으면 null), 진행률을 함께 조회합니다.")
    @GetMapping
    public ApiResponse<AssessmentResumeResponse> resume(
            @PathVariable Integer attemptId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentResponseService.resume(attemptId, authUser.getId()));
    }

    @Operation(summary = "문항 응답 저장(중도저장)", description = "문항 하나에 대한 응답을 저장합니다. 이미 저장된 응답이 있으면 값을 갱신합니다(upsert).")
    @PutMapping("/{questionId}")
    public ApiResponse<AssessmentResponseSaveResponse> saveResponse(
            @PathVariable Integer attemptId,
            @PathVariable Integer questionId,
            @Valid @RequestBody AssessmentResponseSaveRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentResponseService.saveResponse(
                attemptId, questionId, request.selectedValue(), authUser.getId()));
    }
}
