package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.posting.*;
import com.gnagnoohc.scms.domain.career.service.JobPostingService;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 교직원/관리자 전용 채용공고 관리 API 컨트롤러
 *
 * <p><strong>[설정 정보]</strong></p>
 * <ul>
 *   <li><b>기본 경로:</b> {@code /api/staff/career/job-postings}</li>
 *   <li><b>접근 권한:</b> 교직원({@code ST100}) 및 관리자({@code AD100}) 전용</li>
 *   <li><b>인증 방식:</b> {@code Authorization: Bearer <JWT_ACCESS_TOKEN>}</li>
 * </ul>
 *
 * <p><strong>[엔드포인트 명세]</strong></p>
 * <pre>
 * 1. 전체 공고 및 검수 목록 조회
 *    - GET /api/staff/career/job-postings
 *    - Param: reviewStatus, postingStatus, ncsCodeId, regionCodeId, page, size
 *    - 정책 : 검수 대기/승인/반려/마감 전체 상태 이력 모니터링 (최신등록순 정렬)
 *
 * 2. 구인공고 신규 등록 (구인 신청 접수)
 *    - POST /api/staff/career/job-postings
 *    - Body : JobPostingCreateRequestDTO
 *    - 정책 : 초기 상태는 검수대기(REQUESTED) / DRAFT 로 생성
 *
 * 3. 공고 내용 수정
 *    - PUT /api/staff/career/job-postings/{jobPostingId}
 *    - Body : JobPostingUpdateRequestDTO
 *
 * 4. 공고 검수 (승인 / 반려) 처리
 *    - PATCH /api/staff/career/job-postings/{jobPostingId}/review
 *    - Body : JobPostingReviewRequestDTO (reviewStatus, rejectionReason)
 *    - 정책 : 승인 시 즉시 게시 PUBLISHED, 반려 시 사유 필수 기록 및 DRAFT 유지
 *
 * 5. 공고 데이터 삭제
 *    - DELETE /api/staff/career/job-postings/{jobPostingId}
 * </pre>
 *
 * @author YUN
 */
@Tag(name = "[교직원] 채용공고 관리 API", description = "취창업지원과 관리자 전용 구인 등록, 검수, 수정, 삭제")
@RestController
@RequestMapping("/api/staff/career/job-postings")
@RequiredArgsConstructor
@PreAuthorize("@careerSecurity.isCareerStaff(principal)")
public class JobPostingAdminController {

    private final JobPostingService jobPostingService;

    @Operation(summary = "채용공고 전체 및 검수 목록 조회",
            description = "검수 상태(대기/승인/반려/마감)를 포함한 운영용 전체 공고 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<JobPostingSummaryResponseDTO>> getStaffJobPostings(
            @ModelAttribute JobPostingSearchConditionDTO condition,
            @PageableDefault(size = 10, sort = "jobPostingId", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<JobPostingSummaryResponseDTO> response = jobPostingService.getStaffJobPostings(condition, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "채용공고 신규 등록 (구인 신청)",
            description = "신규 공고를 등록(DRAFT/REQUESTED)합니다.")
    @PostMapping
    public ResponseEntity<Integer> createJobPosting(
            @Valid @RequestBody JobPostingCreateRequestDTO requestDTO) {

        Integer createdId = jobPostingService.createJobPosting(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdId);
    }

    @Operation(summary = "채용공고 내용 수정",
            description = "공고 내용 및 자격 요건을 수정합니다.")
    @PutMapping("/{jobPostingId}")
    public ResponseEntity<Void> updateJobPosting(
            @Parameter(description = "채용공고 식별자 (PK)", example = "1")
            @PathVariable Integer jobPostingId,
            @Valid @RequestBody JobPostingUpdateRequestDTO requestDTO) {

        jobPostingService.updateJobPosting(jobPostingId, requestDTO);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "채용공고 검수 (승인/반려) 처리",
            description = "공고를 승인(PUBLISHED)하거나 반려 사유와 함께 반려(REJECTED)합니다.")
    @PatchMapping("/{jobPostingId}/review")
    public ResponseEntity<Void> reviewJobPosting(
            @Parameter(description = "채용공고 식별자 (PK)", example = "1")
            @PathVariable Integer jobPostingId,
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody JobPostingReviewRequestDTO requestDTO) {

        jobPostingService.reviewJobPosting(jobPostingId, authUser.getId(), requestDTO);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "채용공고 삭제",
            description = "채용공고를 삭제합니다.")
    @DeleteMapping("/{jobPostingId}")
    public ResponseEntity<Void> deleteJobPosting(
            @Parameter(description = "채용공고 식별자 (PK)", example = "1")
            @PathVariable Integer jobPostingId) {

        jobPostingService.deleteJobPosting(jobPostingId);
        return ResponseEntity.noContent().build();
    }
}
