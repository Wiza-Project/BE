package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentDistributionResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentDistributionService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssessmentDistribution", description = "진단 결과 통계 - 역량별 분포·집단별 비교 조회")
@RestController
@RequestMapping("/api/staff/assessment-rounds")
@RequiredArgsConstructor
public class AssessmentDistributionController {

    private final AssessmentDistributionService assessmentDistributionService;

    @Operation(summary = "역량별 분포·집단별 비교 조회", description = "회차의 역량별 평균 환산점수를 그룹 축(GRADE: 학년, MAJOR: 전공)별로 집계합니다. "
            + "역량별 분포 그래프와 집단별 비교 그래프가 같은 응답 구조를 재사용합니다.")
    @GetMapping("/{roundId}/stats/distribution")
    public ApiResponse<AssessmentDistributionResponse> getDistribution(
            @PathVariable Integer roundId,
            @RequestParam String groupBy) {
        return ApiResponse.ok(assessmentDistributionService.getDistribution(roundId, groupBy));
    }
}
