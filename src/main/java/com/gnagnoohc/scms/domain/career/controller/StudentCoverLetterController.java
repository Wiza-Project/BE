package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterUpdateRequestDTO;
import com.gnagnoohc.scms.domain.career.service.CoverLetterService;
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

/**
 * 학생 전용 자기소개서 API 컨트롤러
 *
 * <p><strong>[설정 정보]</strong></p>
 * <ul>
 *   <li><b>기본 경로:</b> {@code /api/v1/students/me/cover-letters}</li>
 *   <li><b>접근 권한:</b> {@code ROLE_SD100(학생)} 전용</li>
 * </ul>
 */
@Tag(name = "03-03. 학생 자기소개서 API", description = "학생 본인의 자기소개서 문항별 작성, 조회, 수정, 삭제 및 버전 관리")
@RestController
@RequestMapping("/api/v1/students/me/cover-letters")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SD100')")
public class StudentCoverLetterController {

    private final CoverLetterService coverLetterService;

    @Operation(summary = "내 자기소개서 목록(버전 이력) 조회", description = "본인이 작성한 자기소개서를 버전 최신순으로 페이징 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<CoverLetterSummaryResponseDTO>> getMyCoverLetters(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10, sort = "versionNo", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(coverLetterService.getMyCoverLetters(authUser.getId(), pageable));
    }

    @Operation(summary = "내 최신 버전 자기소개서 조회", description = "본인의 가장 최근 버전 자기소개서를 조회합니다.")
    @GetMapping("/latest")
    public ApiResponse<CoverLetterResponseDTO> getMyLatestCoverLetter(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(coverLetterService.getMyLatestCoverLetter(authUser.getId()));
    }

    @Operation(summary = "내 자기소개서 특정 버전 조회", description = "본인 소유의 특정 버전 자기소개서를 조회합니다.")
    @GetMapping("/{careerDocumentId}")
    public ApiResponse<CoverLetterResponseDTO> getMyCoverLetter(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId) {
        return ApiResponse.ok(coverLetterService.getMyCoverLetter(authUser.getId(), careerDocumentId));
    }

    @Operation(summary = "자기소개서 최초 작성", description = "문항별 답변을 담아 자기소개서(버전 1)를 신규 작성합니다. 이미 작성된 이력이 있으면 실패합니다.")
    @PostMapping
    public ApiResponse<CoverLetterResponseDTO> createCoverLetter(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CoverLetterCreateRequestDTO requestDTO) {
        return ApiResponse.ok(coverLetterService.createCoverLetter(authUser.getId(), requestDTO));
    }

    @Operation(summary = "자기소개서 내용 수정", description = "지정한 버전의 자기소개서 내용을 그대로 수정합니다 (새 버전을 만들지 않음).")
    @PutMapping("/{careerDocumentId}")
    public ApiResponse<CoverLetterResponseDTO> updateCoverLetter(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId,
            @Valid @RequestBody CoverLetterUpdateRequestDTO requestDTO) {
        return ApiResponse.ok(coverLetterService.updateCoverLetter(authUser.getId(), careerDocumentId, requestDTO));
    }

    @Operation(summary = "자기소개서 새 버전 생성", description = "지정한 버전의 현재 내용을 스냅샷하여 새 버전을 생성합니다. 과거 버전을 지정하면 그 내용으로 되돌리는 효과를 냅니다.")
    @PostMapping("/{careerDocumentId}/versions")
    public ApiResponse<CoverLetterResponseDTO> createNextVersion(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId) {
        return ApiResponse.ok(coverLetterService.createNextVersion(authUser.getId(), careerDocumentId));
    }

    @Operation(summary = "자기소개서 삭제", description = "본인 소유의 특정 버전 자기소개서를 삭제합니다.")
    @DeleteMapping("/{careerDocumentId}")
    public ApiResponse<Void> deleteCoverLetter(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId) {
        coverLetterService.deleteCoverLetter(authUser.getId(), careerDocumentId);
        return ApiResponse.ok();
    }
}
