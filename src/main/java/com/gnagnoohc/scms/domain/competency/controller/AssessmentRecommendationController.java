package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.response.RecommendedProgramsResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentRecommendationService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssessmentRecommendation", description = "학생 본인의 진단 결과 기반 추천 비교과 프로그램 조회")
@RestController
@RequestMapping("/api/students/assessment-attempts/{attemptId}/recommended-programs")
@RequiredArgsConstructor
public class AssessmentRecommendationController {

    private final AssessmentRecommendationService assessmentRecommendationService;

    @Operation(summary = "추천 비교과 프로그램 조회",
            description = "해당 응시(attemptId) 결과에서 환산점수가 낮은 취약 역량을 골라, 그 역량에 연계된 모집중 비교과 "
                    + "프로그램을 취약 역량 순으로 반환합니다. 각 프로그램의 상세/점수 차트는 결과 조회 API를 재사용하세요. "
                    + "응시 소유자가 아니거나 아직 채점되지 않았으면 결과 조회 API와 동일하게 차단됩니다. "
                    + "취약 역량이 없거나 조건에 맞는 프로그램이 없으면 빈 목록을 반환합니다.")
    @GetMapping
    public ApiResponse<RecommendedProgramsResponse> getRecommendations(
            @PathVariable Integer attemptId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentRecommendationService.recommend(attemptId, authUser.getId()));
    }
}
