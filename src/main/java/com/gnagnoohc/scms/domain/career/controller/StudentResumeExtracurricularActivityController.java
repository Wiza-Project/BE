package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.extracurricular.ResumeExtracurricularActivityResponse;
import com.gnagnoohc.scms.domain.career.service.ResumeExtracurricularActivityService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "학생 이력서 비교과 이력 API", description = "이력서 화면의 비교과 수료 이력 자동연동 조회")
@RestController
@RequestMapping("/api/students/me/resume/extracurricular-activities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SD100')")
public class StudentResumeExtracurricularActivityController {

    private final ResumeExtracurricularActivityService resumeExtracurricularActivityService;

    /** 본인의 비교과 수료 이력을 이수일 기준 최신순으로 조회한다. 이력이 없으면 빈 목록을 반환한다. */
    @GetMapping
    public ApiResponse<List<ResumeExtracurricularActivityResponse>> getMyActivities(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(resumeExtracurricularActivityService.getMyActivities(authUser.getId()));
    }
}
