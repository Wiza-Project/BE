package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.resume.ResumeCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeUpdateRequestDTO;
import com.gnagnoohc.scms.domain.career.service.ResumeService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03-02. 학생 이력서 API", description = "학생 본인의 고정 이력서 템플릿 저장, 조회 및 버전 관리")
@RestController
@RequestMapping("/api/v1/students/me/resumes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SD100')")
public class StudentResumeController {

    private final ResumeService resumeService;

    @GetMapping
    public ApiResponse<PageResponse<ResumeSummaryResponseDTO>> getMyResumes(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10, sort = "versionNo", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(resumeService.getMyResumes(authUser.getId(), pageable));
    }

    @GetMapping("/latest")
    public ApiResponse<ResumeResponseDTO> getMyLatestResume(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(resumeService.getMyLatestResume(authUser.getId()));
    }

    @GetMapping("/{careerDocumentId}")
    public ApiResponse<ResumeResponseDTO> getMyResume(
            @AuthenticationPrincipal AuthUser authUser, @PathVariable Integer careerDocumentId) {
        return ApiResponse.ok(resumeService.getMyResume(authUser.getId(), careerDocumentId));
    }

    @PostMapping
    public ApiResponse<ResumeResponseDTO> createResume(
            @AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody ResumeCreateRequestDTO request) {
        return ApiResponse.ok(resumeService.createResume(authUser.getId(), request));
    }

    @PutMapping("/{careerDocumentId}")
    public ApiResponse<ResumeResponseDTO> updateResume(
            @AuthenticationPrincipal AuthUser authUser, @PathVariable Integer careerDocumentId,
            @Valid @RequestBody ResumeUpdateRequestDTO request) {
        return ApiResponse.ok(resumeService.updateResume(authUser.getId(), careerDocumentId, request));
    }

    @PostMapping("/{careerDocumentId}/versions")
    public ApiResponse<ResumeResponseDTO> createNextVersion(
            @AuthenticationPrincipal AuthUser authUser, @PathVariable Integer careerDocumentId) {
        return ApiResponse.ok(resumeService.createNextVersion(authUser.getId(), careerDocumentId));
    }

    @DeleteMapping("/{careerDocumentId}")
    public ApiResponse<Void> deleteResume(
            @AuthenticationPrincipal AuthUser authUser, @PathVariable Integer careerDocumentId) {
        resumeService.deleteResume(authUser.getId(), careerDocumentId);
        return ApiResponse.ok();
    }
}
