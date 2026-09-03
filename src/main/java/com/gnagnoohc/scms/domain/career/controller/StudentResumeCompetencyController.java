package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.competency.ResumeCompetencyResponse;
import com.gnagnoohc.scms.domain.career.service.ResumeCompetencyService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "학생 이력서 핵심역량 연동 API", description = "이력서 화면의 핵심역량 진단 결과 자동연동 조회 및 재연동 요청")
@RestController
@RequestMapping("/api/students/me/resume/competency")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SD100')")
public class StudentResumeCompetencyController {

    private final ResumeCompetencyService resumeCompetencyService;

    /** 저장된 최신 연동 결과를 그대로 조회한다 — 호출로 인한 재연동은 일어나지 않는다. */
    @GetMapping
    public ApiResponse<ResumeCompetencyResponse> getLatest(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(resumeCompetencyService.getLatest(authUser.getId()));
    }

    /** 재연동 버튼 — 핵심역량 도메인에 최신 완료 진단을 다시 요청하고, 반영된 결과를 바로 응답한다. */
    @PostMapping
    public ApiResponse<ResumeCompetencyResponse> resync(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(resumeCompetencyService.refresh(authUser.getId()));
    }
}
