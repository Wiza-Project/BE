package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingDetailResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSearchConditionDTO;
import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.service.JobPostingService;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.helper.FileUploadValidator;
import com.gnagnoohc.scms.global.common.repository.FileGroupRepository;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 학생 전용 채용공고 API 컨트롤러
 *
 * <p><strong>[설정 정보]</strong></p>
 * <ul>
 *   <li><b>기본 경로:</b> {@code /api/v1/job-postings}</li>
 *   <li><b>접근 권한:</b> 전체 공개 (비인가 게스트 및 학생 {@code SD100} 조회 허용)</li>
 * </ul>
 *
 * <p><strong>[엔드포인트 명세]</strong></p>
 * <pre>
 * 1. 채용공고 목록 조회 (필터/페이징)
 *    - GET /api/v1/job-postings
 *    - Param: ncsCodeId, regionCodeId, companyName, postingType, employmentType, page, size
 *    - 정책 : 게시승인(PUBLISHED) + 접수마감 미경과 건만 노출 (마감일 임박순 기본 정렬)
 *
 * 2. 채용공고 단건 상세 조회
 *    - GET /api/v1/job-postings/{jobPostingId}
 *    - 정책 : 공고 본문, 직무/지역명, 자격요건(JSON), 추천채용 혜택 반환
 * </pre>
 *
 * @author YUN
 */
@Tag(name = "[학생] 채용공고 API", description = "학생 사용자 전용 채용공고 탐색 및 상세 조회")
@RestController
@RequestMapping("/api/students/career/job-postings")
@RequiredArgsConstructor
public class JobPostingStudentController {

    private final JobPostingService jobPostingService;

    private final FileGroupService fileGroupService;
    private final FileStorageService fileStorageService;
    private final FileGroupRepository fileGroupRepository;

    @Operation(summary = "학생 채용공고 목록 조회 (필터/페이징)",
            description = "게시 승인(PUBLISHED) 및 마감일이 지나지 않은 유효 공고만 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<JobPostingSummaryResponseDTO>> getStudentJobPostings(
            @ModelAttribute JobPostingSearchConditionDTO condition,
            @PageableDefault(size = 10, sort = "applicationEndsAt", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<JobPostingSummaryResponseDTO> response = jobPostingService.getStudentJobPostings(condition, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "채용공고 단건 상세 조회",
            description = "채용공고의 상세 직무 요건 및 혜택 정보를 조회합니다.")
    @GetMapping("/{jobPostingId}")
    public ResponseEntity<JobPostingDetailResponseDTO> getJobPostingDetail(
            @Parameter(description = "채용공고 식별자 (PK)", example = "1")
            @PathVariable Integer jobPostingId) {

        JobPostingDetailResponseDTO response = jobPostingService.getJobPostingDetail(jobPostingId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "공고 포스터 이미지 인라인 조회 (비로그인 허용)")
    @GetMapping("/posters/{fileGroupId}")
    public ResponseEntity<org.springframework.core.io.Resource> viewPoster(
            @PathVariable Integer fileGroupId) {

        // FileGroup 내의 첫 번째 파일 조회
        StoredFile storedFile = fileGroupService.getFiles(fileGroupRepository.findById(fileGroupId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        FileStorageService.LoadedFile loaded = fileStorageService.load(storedFile.getStoredFileId());

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(loaded.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(loaded.resource());
    }

}