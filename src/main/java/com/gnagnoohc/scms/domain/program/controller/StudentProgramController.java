package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.competency.dto.CompetencySummary;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramDetailResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.service.ProgramService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "StudentProgram", description = "학생의 비교과 프로그램 목록 조회")
@RestController
@RequestMapping("/api/students/programs")
@RequiredArgsConstructor
public class StudentProgramController {

    private final ProgramService programService;

    @Operation(summary = "프로그램 목록 조회", description = "상태/이름 키워드로 비교과 프로그램 목록을 페이지 단위로 조회합니다")
    // HTTP GET 요청, 즉 "/api/students/programs" 로 오는 요청을 이 메서드가 처리한다.
    @GetMapping
    public ApiResponse<PageResponse<ProgramListItemResponseDTO>> list(
            // 모집중/운영중/종료 중 하나로 필터링. 생략하면 전체 상태를 조회한다.
            @RequestParam(required = false) ProgramStatus status,
            // 프로그램명 부분 일치(대소문자 무시) 검색어. 생략하면 이름으로 거르지 않는다.
            @RequestParam(required = false) String keyword,
            // 연계 핵심역량 id로 필터링. 생략하면 역량으로 거르지 않는다.
            @RequestParam(required = false) Integer competencyId,
            /**
             * page/size/sort 쿼리 파라미터를 스프링이 자동으로 Pageable로 변환한다.
             * 기본값은 최신 등록순 20건씩. 마감임박순 등 다른 정렬은 sort=recruitmentEndsAt,asc 처럼 그대로 넘기면 된다
             * (ExtracurricularProgramRepositoryImpl.resolveOrderSpecifiers가 허용하는 필드만 화이트리스트로 반영한다).
             */
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            // 지금 로그인해서 이 요청을 보낸 학생의 id (인증 정보에서 옴). 카드별 "내 신청 상태"를 채우는 데 쓴다.
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programService.list(status, keyword, competencyId, authUser.getId(), pageable));
    }

    @Operation(summary = "핵심역량 옵션 조회", description = "핵심역량 목록을 조회합니다")
    @GetMapping("/competencies")
    public ApiResponse<List<CompetencySummary>> listCompetencyOptions() {
        return ApiResponse.ok(programService.getCompetencyOptions());
    }

    @Operation(summary = "프로그램 상세 조회", description = "프로그램 기본정보, 회차 목록, 신청자 수를 조회합니다")
    @GetMapping("/{programId}")
    public ApiResponse<ProgramDetailResponseDTO> getDetail(@PathVariable Integer programId,
                                                             @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programService.getDetail(programId, authUser.getId()));
    }

    @Operation(summary = "운영계획서 다운로드", description = "프로그램에 등록된 운영계획서 원본 파일을 내려받습니다")
    @GetMapping("/{programId}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Integer programId,
                                                  @AuthenticationPrincipal AuthUser authUser) {
        FileStorageService.LoadedFile loadedFile = programService.downloadOperationPlan(programId);
        String contentType = loadedFile.contentType() != null
                ? loadedFile.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // filename*(RFC 5987)에 원본 파일명을 UTF-8 퍼센트 인코딩해 넣고, filename*를 읽지 못하는
        // 구형 클라이언트를 위해 filename에는 원본 파일명에서 비ASCII 문자만 치환한 값을 fallback으로 넣는다.
        String originalFileName = loadedFile.originalFileName();
        String asciiFileName = originalFileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
        String encodedFileName = URLEncoder.encode(originalFileName, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A");
        String contentDisposition = "attachment; filename=\"" + asciiFileName + "\"; filename*=UTF-8''" + encodedFileName;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", contentDisposition)
                .body(loadedFile.resource());
    }
}
