package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.relation.JobRelationRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.relation.JobRelationResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.relation.JobScrapSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.relation.JobScrapToggleResponseDTO;
import com.gnagnoohc.scms.domain.career.service.StudentJobRelationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 전용 채용공고 관계 컨트롤러 (온라인 지원, 관심 공고 스크랩, 지원 현황 조회)
 *
 * <p><strong>[인가 및 보안 규칙]</strong></p>
 * <ul>
 *   <li>{@code ROLE_STUDENT(SD100)} 권한을 가진 학생 사용자만 접근 가능</li>
 *   <li>로그인 사용자 식별자는 {@link AuthenticationPrincipal}의 {@link AuthUser}에서 안전하게 추출</li>
 * </ul>
 *
 * @author YUN
 */
@Tag(name = "학생 - 취창업 관계 관리 API", description = "온라인 채용 지원, 관심 공고 스크랩 토글 및 지원 현황 조회 API")
@RestController
@RequestMapping("/api/v1/career")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SD100')")
public class StudentJobRelationController {

    private final StudentJobRelationService relationService;

    @PostMapping("/applications")
    @Operation(summary = "채용공고 온라인 지원 신청", description = "학생이 특정 채용공고에 온라인 지원을 제출합니다.")
    public ApiResponse<JobRelationResponseDTO> applyJob(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody JobRelationRequestDTO requestDTO) {
        JobRelationResponseDTO response = relationService.applyJob(authUser.getId(), requestDTO);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/applications/{jobPostingId}")
    @Operation(summary = "채용공고 지원 취소", description = "접수 마감 전 지원한 채용공고 지원을 취소합니다.")
    public ApiResponse<Void> cancelApplication(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer jobPostingId) {
        relationService.cancelApplication(authUser.getId(), jobPostingId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/scraps/{jobPostingId}")
    @Operation(summary = "관심 공고 스크랩 토글", description = "관심 공고를 보관함에 등록하거나 해제합니다.")
    public ApiResponse<JobScrapToggleResponseDTO> toggleScrap(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer jobPostingId) {
        JobScrapToggleResponseDTO response = relationService.toggleScrap(authUser.getId(), jobPostingId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/scraps")
    @Operation(summary = "내 관심 공고 스크랩 목록 조회", description = "학생 본인이 스크랩한 관심 공고 목록을 마감 임박순으로 페이징 조회합니다.")
    public ApiResponse<PageResponse<JobScrapSummaryResponseDTO>> getMyScraps(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10) Pageable pageable) {
        PageResponse<JobScrapSummaryResponseDTO> response = relationService.getMyScrappedPostings(authUser.getId(), pageable);
        return ApiResponse.ok(response);
    }

    @GetMapping("/applications")
    @Operation(summary = "내 지원 내역 목록 조회", description = "학생 본인의 온라인 채용 지원 내역 및 전형 진행 상태를 최신 지원순으로 페이징 조회합니다.")
    public ApiResponse<PageResponse<JobRelationResponseDTO>> getMyApplications(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<JobRelationResponseDTO> response = relationService.getMyApplications(authUser.getId(), pageable);
        return ApiResponse.ok(response);
    }
}