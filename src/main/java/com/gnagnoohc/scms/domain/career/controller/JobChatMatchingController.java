package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.posting.JobChatRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.posting.JobChatResponseDTO;
import com.gnagnoohc.scms.domain.career.service.JobChatMatchingService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 자연어로 채용공고를 찾는 LLM 채팅 API. */
@Tag(name = "Career - Job Chat", description = "LLM 기반 채용공고 채팅 추천 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students/career/job-chat")
public class JobChatMatchingController {

    private final JobChatMatchingService jobChatMatchingService;

    @Operation(summary = "자연어 채용공고 추천", description = "사용자 조건을 해석하여 DB의 게시 중 공고 최대 3건을 추천합니다.")
    @PostMapping
    public ApiResponse<JobChatResponseDTO> chat(@Valid @RequestBody JobChatRequestDTO request) {
        return ApiResponse.ok(jobChatMatchingService.chat(request.message()));
    }
}
