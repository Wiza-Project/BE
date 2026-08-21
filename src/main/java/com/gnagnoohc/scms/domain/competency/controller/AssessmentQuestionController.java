package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentQuestionUploadResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentQuestionService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "AssessmentQuestion", description = "진단문항 관리")
@RestController
@RequestMapping("/api/admin/assessment-questions")
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
}
