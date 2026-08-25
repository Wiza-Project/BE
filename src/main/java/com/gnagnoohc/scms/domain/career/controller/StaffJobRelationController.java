package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.relation.JobRelationResponseDTO;
import com.gnagnoohc.scms.domain.career.service.StudentJobRelationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 교직원/관리자 전용 채용공고 관계 컨트롤러 (공고별 지원자 목록 조회 및 전형 단계 관리)
 *
 * <p><strong>[인가 및 보안 규칙]</strong></p>
 * <ul>
 *   <li>취창업지원과({@code D400}) 교직원 및 총괄 관리자({@code ADMIN}) 권한 보유자만 접근 가능</li>
 * </ul>
 *
 * @author YUN
 */
@Tag(name = "교직원 - 취창업 관계 관리 API", description = "공고별 지원자 목록 및 전형 진행 상태 관리 API")
@RestController
@RequestMapping("/api/v1/admin/career")
@RequiredArgsConstructor
@PreAuthorize("@careerSecurity.isCareerStaff(principal)")
public class StaffJobRelationController {

    private final StudentJobRelationService relationService;

    @GetMapping("/postings/{jobPostingId}/applicants")
    @Operation(summary = "공고별 지원자 목록 조회", description = "특정 채용공고에 지원한 학생 목록과 전형 단계를 선착순/지원일시순으로 페이징 조회합니다.")
    public ApiResponse<PageResponse<JobRelationResponseDTO>> getApplicantsByJobPosting(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer jobPostingId,
            @Parameter(description = "전형 상태 필터 조건 (APPLIED, DOCUMENT_PASS, FINAL_PASS 등, 미지정 시 전체)")
            @RequestParam(required = false) String applicationStatus,
            @PageableDefault(size = 10, sort = "appliedAt", direction = Sort.Direction.ASC) Pageable pageable) {

        PageResponse<JobRelationResponseDTO> response = relationService.getApplicantsByJobPosting(authUser.getId(), jobPostingId, applicationStatus, pageable);
        return ApiResponse.ok(response);
    }
}