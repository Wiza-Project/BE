package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentHistoryResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentHistoryService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssessmentHistory", description = "학생 본인의 과거 진단 결과 목록 조회")
@RestController
@RequestMapping("/api/students/assessment-history")
@RequiredArgsConstructor
public class AssessmentHistoryController {

    private final AssessmentHistoryService assessmentHistoryService;

    @Operation(summary = "과거 진단 결과 목록 조회", description = "본인이 응시완료(제출)한 회차를 최신순으로 페이지 단위 조회합니다. "
            + "keyword는 진단명(assessmentName) 부분일치 검색입니다. 상세 점수는 결과 조회 API(/api/students/assessment-attempts/{attemptId}/result)를 재사용하세요.")
    @GetMapping
    public ApiResponse<PageResponse<AssessmentHistoryResponse>> getHistory(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentHistoryService.getHistory(authUser.getId(), keyword, pageable));
    }
}
