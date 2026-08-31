package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.CompetencySummary;
import com.gnagnoohc.scms.domain.competency.service.CompetencyQueryService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 핵심역량 "선택 목록" 조회.
 *
 * <p>등록·축순서·사용여부 변경(CompetencyController, /api/staff/** = 교직원 전용)과 달리, 이 조회는
 * 교직원 전용 경로가 아니라 인증만 요구하는 /api/competencies 에 둔다(SecurityConfig의 anyRequest().authenticated()).
 * 따라서 로그인한 사용자면 유형과 무관하게 호출할 수 있다 — 노출되는 값은 역량 이름·표시순서뿐이라
 * 민감정보가 아니기 때문이다. 실제 사용처는:
 * <ul>
 *   <li>학생: 비교과 프로그램 목록을 핵심역량으로 필터링</li>
 *   <li>교직원: 비교과 프로그램 등록·수정 폼 / 마일리지 활동 유형 등록 폼에서 competency_id 선택</li>
 * </ul>
 */
@Tag(name = "Competency", description = "핵심역량 관리")
@RestController
@RequestMapping("/api/competencies")
@RequiredArgsConstructor
public class CompetencyQueryController {

    private final CompetencyQueryService competencyQueryService;

    @Operation(summary = "핵심역량 목록 조회", description = "활성 최상위 핵심역량을 축순서대로 조회합니다. "
            + "비교과 프로그램 필터·등록, 마일리지 활동유형 등록 등에서 핵심역량을 고르는 드롭다운에 공용으로 사용합니다. "
            + "로그인한 사용자면 누구나 호출할 수 있습니다. "
            + "includeCompetencyId를 넘기면 그 역량이 비활성이라 기본 목록에서 빠졌더라도 결과 끝에 함께 포함합니다"
            + "(프로그램 수정 폼에서 현재 연결된 역량을 표시하기 위한 용도).")
    @GetMapping
    public ApiResponse<List<CompetencySummary>> listCompetencies(
            @RequestParam(required = false) Integer includeCompetencyId
    ) {
        return ApiResponse.ok(competencyQueryService.getActiveTopLevelCompetencies(includeCompetencyId));
    }
}
