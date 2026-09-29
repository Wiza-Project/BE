package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.ai.CareerAiAssistRequest;
import com.gnagnoohc.scms.domain.career.dto.ai.CareerAiAssistResponse;
import com.gnagnoohc.scms.domain.career.service.CareerAiAssistantService;
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

/** 이력서·포트폴리오 작성 보조용 AI API. */
@Tag(name = "학생 취창업 AI 작성 보조 API", description = "이력서·포트폴리오 문장 초안을 AI로 생성")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/me/career-ai")
@PreAuthorize("hasRole('SD100')")
public class CareerAiAssistantController {

    private final CareerAiAssistantService careerAiAssistantService;

    @Operation(
            summary = "이력서·포트폴리오 문장 초안 생성",
            description = """
                    입력한 사실(context)을 바탕으로 AI가 문장 초안 하나를 생성해 돌려준다.
                    """
    )
    @PostMapping("/assist")
    public ApiResponse<CareerAiAssistResponse> assist(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CareerAiAssistRequest request) {
        return ApiResponse.ok(careerAiAssistantService.generate(authUser.getId(), request));
    }
}
