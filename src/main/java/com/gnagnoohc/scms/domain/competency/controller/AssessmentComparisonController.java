package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentComparisonResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentComparisonService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssessmentComparison", description = "학생 본인의 사전·사후 진단 결과 비교")
@RestController
@RequestMapping("/api/students/assessment-comparison")
@RequiredArgsConstructor
public class AssessmentComparisonController {

    private final AssessmentComparisonService assessmentComparisonService;

    @Operation(summary = "사전·사후 비교 조회",
            description = "선택한 두 응시(attemptId)의 결과를 사전 → 사후 순으로 정렬해 겹친 방사형 차트용 점수와 "
                    + "역량별 변화량(afterScore - beforeScore, 하락도 그대로)을 반환합니다. 두 attemptId의 전달 순서는 무관하며, "
                    + "서버가 회차 구분(PRE/POST)으로 사전·사후 방향을 결정합니다. 비교 대상은 같은 학년도의 PRE 1건과 POST 1건이어야 하며, "
                    + "같은 구분 2건이거나 학년도가 다르면 Q023, 같은 attemptId를 중복 지정하면 Q022를 반환합니다. "
                    + "각 응시의 상세 점수 계산은 결과 조회 API와 동일합니다.")
    @GetMapping
    public ApiResponse<AssessmentComparisonResponse> compare(
            @RequestParam Integer firstAttemptId,
            @RequestParam Integer secondAttemptId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentComparisonService.compare(firstAttemptId, secondAttemptId, authUser.getId()));
    }
}
