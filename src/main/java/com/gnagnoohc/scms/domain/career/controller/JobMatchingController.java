package com.gnagnoohc.scms.domain.career.controller;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.service.JobMatchingService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Career - Job Matching", description = "학생 맞춤 채용공고 추천 API")
@RestController
@RequestMapping("/api/students/career/matching")
@RequiredArgsConstructor
public class JobMatchingController {

    private final JobMatchingService jobMatchingService;

    @Operation(
            summary = "학생 맞춤 채용공고 목록 조회",
            description = "로그인한 학생의 프로필 임베딩 벡터 코사인 유사도 기반 맞춤 추천 채용공고 목록을 반환합니다. (PROFILING 미동의 시 기본 최신 공고 반환)"
    )
    @GetMapping("/recommendations")
    public ApiResponse<List<JobPostingSummaryResponseDTO>> getRecommendations(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        List<JobPostingSummaryResponseDTO> response = jobMatchingService.getRecommendedPostingsForStudent(authUser.getId());
        return ApiResponse.ok(response);
    }
}
