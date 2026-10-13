package com.gnagnoohc.scms.domain.counsel.controller;

import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiChatRequest;
import com.gnagnoohc.scms.domain.counsel.dto.ai.CounselingAiChatResponse;
import com.gnagnoohc.scms.domain.counsel.service.CounselingAiAssistantService;
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

/** 학생 상담 전 AI 도우미 API. 기존 상담 신청·검사 API는 변경하지 않는다. */
@Tag(name = "Counsel - AI Assistant", description = "상담 전 챗봇 및 상담 일정 추천 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/counseling-ai")
@PreAuthorize("hasRole('STUDENT')")
public class CounselingAiAssistantController {

    private final CounselingAiAssistantService counselingAiAssistantService;

    @Operation(summary = "상담 전 AI 안내 및 예약 가능 일정 추천")
    @PostMapping("/chat")
    public ApiResponse<CounselingAiChatResponse> chat(
            @Valid @RequestBody CounselingAiChatRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(counselingAiAssistantService.chat(authUser.getId(), request));
    }
}
