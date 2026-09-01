package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.request.AssessmentQuestionEditRequest;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentQuestionResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentQuestionUploadResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentQuestionService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "AssessmentQuestion", description = "진단문항 관리")
@RestController
@RequestMapping("/api/staff/assessment-questions")
@RequiredArgsConstructor
public class AssessmentQuestionController {

    private final AssessmentQuestionService assessmentQuestionService;

    @Operation(summary = "진단문항 엑셀 일괄 업로드",
            description = "엑셀 파일(상위역량|하위역량|문항번호|평가문항)로 진단문항을 일괄 등록합니다. "
                    + "소속 핵심역량은 '상위역량' 컬럼(역량명)으로 매핑되고, 하위역량·문항번호 컬럼은 무시됩니다. "
                    + "응답옵션은 서버가 고정 5점 리커트를 주입하며, 역문항 여부는 전량 false로 저장됩니다.")
    @PostMapping("/upload")
    public ApiResponse<AssessmentQuestionUploadResponse> uploadQuestions(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentQuestionService.uploadQuestions(file, authUser.getId()));
    }

    @Operation(summary = "핵심역량별 진단문항 목록 조회", description = "특정 핵심역량에 속한 현재 유효(활성) 문항 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<AssessmentQuestionResponse>> getQuestionsByCompetency(
            @RequestParam Integer competencyId
    ) {
        return ApiResponse.ok(assessmentQuestionService.getQuestionsByCompetency(competencyId));
    }

    @Operation(summary = "진단문항 단건 조회", description = "편집 폼 진입 시 문항 상세(내용·응답옵션·역문항 여부)를 조회합니다.")
    @GetMapping("/{questionId}")
    public ApiResponse<AssessmentQuestionResponse> getQuestion(
            @PathVariable Integer questionId
    ) {
        return ApiResponse.ok(assessmentQuestionService.getQuestion(questionId));
    }

    @Operation(summary = "진단문항 편집", description = "문항 내용·응답옵션·역문항 여부를 수정합니다. "
            + "아직 응시 이력이 없는 문항은 제자리 수정되고, 이미 응답이 저장된 문항은 새 버전으로 대체되며 이전 버전은 비활성 처리됩니다.")
    @PatchMapping("/{questionId}")
    public ApiResponse<AssessmentQuestionResponse> editQuestion(
            @PathVariable Integer questionId,
            @Valid @RequestBody AssessmentQuestionEditRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentQuestionService.editQuestion(questionId, request, authUser.getId()));
    }
}
