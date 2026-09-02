package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.request.AssessmentRoundRequest;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentRoundService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AssessmentRound", description = "진단 회차 관리")
@RestController
@RequestMapping("/api/staff/assessment-rounds")
@RequiredArgsConstructor
public class AssessmentRoundController {

    private final AssessmentRoundService assessmentRoundService;

    @Operation(summary = "진단 회차 목록 조회", description = "교직원 회차 관리 화면용 목록입니다. 개설된 회차를 최근순으로 모두 내려줍니다.")
    @GetMapping
    public ApiResponse<List<AssessmentRoundResponse>> getRounds() {
        return ApiResponse.ok(assessmentRoundService.getRounds());
    }

    @Operation(summary = "진단 회차 등록", description = "학년도·학기·진단구분(사전/사후) + 응시기간 + 응시대상 조건을 하나의 API로 함께 등록합니다. "
            + "같은 학년도·학기·진단구분의 회차는 중복 개설할 수 없습니다. targetCondition을 생략하면 전체 학생이 대상입니다.")
    @PostMapping
    public ApiResponse<AssessmentRoundResponse> registerRound(
            @Valid @RequestBody AssessmentRoundRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(assessmentRoundService.registerRound(request, authUser.getId()));
    }

    @Operation(summary = "진단 회차 수정", description = "회차 정보를 수정합니다. 이미 응시(문항 응답)가 시작된 회차는 수정할 수 없습니다.")
    @PatchMapping("/{roundId}")
    public ApiResponse<AssessmentRoundResponse> updateRound(
            @PathVariable Integer roundId,
            @Valid @RequestBody AssessmentRoundRequest request
    ) {
        return ApiResponse.ok(assessmentRoundService.updateRound(roundId, request));
    }
}
