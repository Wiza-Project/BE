package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.request.CompetencyAiChatRequest;
import com.gnagnoohc.scms.domain.competency.dto.response.CompetencyAiChatResponse;
import com.gnagnoohc.scms.domain.competency.service.CompetencyAiAssistantService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생 본인의 제출 완료 핵심역량 결과를 해석하는 AI 도우미 API. */
@Tag(name = "CompetencyAiAssistant", description = "핵심역량 진단 결과 AI 해석 및 보완 방향 안내")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/competency-ai")
@PreAuthorize("hasRole('STUDENT')")
public class CompetencyAiAssistantController {

    private final CompetencyAiAssistantService competencyAiAssistantService;

    @Operation(summary = "핵심역량 진단 결과 AI 해석",
            description = "해당 응시(attemptId)의 결과와 추천 비교과 프로그램을 근거로 질문에 대한 AI 답변을 반환합니다. "
                    + "응시 소유자가 아니거나 아직 채점되지 않았으면 결과 조회 API와 동일하게 차단됩니다. "
                    + "AI 모델이 설정돼 있지 않거나 호출에 실패하면 서버 규칙으로 만든 기본 답변을 반환합니다.")
    @PostMapping("/chat")
    public ApiResponse<CompetencyAiChatResponse> chat(
            @Valid @RequestBody CompetencyAiChatRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(competencyAiAssistantService.chat(authUser.getId(), request));
    }
}
