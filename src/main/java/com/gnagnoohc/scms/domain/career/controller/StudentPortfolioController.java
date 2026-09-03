package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioUpdateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioVisibilityRequestDTO;
import com.gnagnoohc.scms.domain.career.service.PortfolioService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 학생 전용 포트폴리오 API 컨트롤러
 *
 * <p><strong>[설정 정보]</strong></p>
 * <ul>
 *   <li><b>기본 경로:</b> {@code /api/students/me/portfolios}</li>
 *   <li><b>접근 권한:</b> {@code ROLE_SD100(학생)} 전용</li>
 * </ul>
 */
@Tag(name = "03-04. 학생 포트폴리오 API", description = "학생 본인의 포트폴리오 항목 생성, 조회, 수정, 삭제, 공개 여부 변경 및 첨부파일 관리")
@RestController
@RequestMapping("/api/students/me/portfolios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SD100')")
public class StudentPortfolioController {

    private final PortfolioService portfolioService;

    @Operation(summary = "내 포트폴리오 목록 조회", description = "본인의 포트폴리오 항목을 최신 수정순으로 페이징 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<PortfolioSummaryResponseDTO>> getMyPortfolios(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(portfolioService.getMyPortfolios(authUser.getId(), pageable));
    }

    @Operation(summary = "내 포트폴리오 항목 단건 조회", description = "본인 소유의 포트폴리오 항목 상세(첨부파일 포함)를 조회합니다.")
    @GetMapping("/{careerDocumentId}")
    public ApiResponse<PortfolioResponseDTO> getMyPortfolio(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId) {
        return ApiResponse.ok(portfolioService.getMyPortfolio(authUser.getId(), careerDocumentId));
    }

    @Operation(summary = "포트폴리오 항목 생성", description = "새 포트폴리오 항목을 생성합니다.")
    @PostMapping
    public ApiResponse<PortfolioResponseDTO> createPortfolio(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PortfolioCreateRequestDTO requestDTO) {
        return ApiResponse.ok(portfolioService.createPortfolio(authUser.getId(), requestDTO));
    }

    @Operation(summary = "포트폴리오 항목 수정", description = "본인 소유의 포트폴리오 항목 내용을 수정합니다.")
    @PutMapping("/{careerDocumentId}")
    public ApiResponse<PortfolioResponseDTO> updatePortfolio(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId,
            @Valid @RequestBody PortfolioUpdateRequestDTO requestDTO) {
        return ApiResponse.ok(portfolioService.updatePortfolio(authUser.getId(), careerDocumentId, requestDTO));
    }

    @Operation(summary = "포트폴리오 공개 여부 변경", description = "포트폴리오 항목의 공개/비공개 상태를 변경합니다.")
    @PatchMapping("/{careerDocumentId}/visibility")
    public ApiResponse<PortfolioResponseDTO> changeVisibility(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId,
            @Valid @RequestBody PortfolioVisibilityRequestDTO requestDTO) {
        return ApiResponse.ok(portfolioService.changeVisibility(authUser.getId(), careerDocumentId, requestDTO.getIsPublic()));
    }

    @Operation(summary = "포트폴리오 삭제", description = "본인 소유의 포트폴리오 항목과 첨부파일을 함께 삭제합니다.")
    @DeleteMapping("/{careerDocumentId}")
    public ApiResponse<Void> deletePortfolio(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId) {
        portfolioService.deletePortfolio(authUser.getId(), careerDocumentId);
        return ApiResponse.ok();
    }

    @Operation(summary = "포트폴리오 첨부파일 업로드", description = "포트폴리오 항목에 이미지/PDF 첨부파일을 연결합니다.")
    @PostMapping(value = "/{careerDocumentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PortfolioResponseDTO> uploadAttachments(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId,
            @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok(portfolioService.attachFiles(authUser.getId(), careerDocumentId, files));
    }

    @Operation(summary = "포트폴리오 첨부파일 다운로드", description = "본인 소유 포트폴리오에 연결된 첨부파일을 다운로드합니다.")
    @GetMapping("/{careerDocumentId}/attachments/{storedFileId}")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer careerDocumentId,
            @PathVariable Integer storedFileId) {
        FileStorageService.LoadedFile loaded = portfolioService.downloadAttachment(authUser.getId(), careerDocumentId, storedFileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(loaded.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(loaded.originalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(loaded.resource());
    }
}
