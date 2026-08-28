package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.CompetencyActiveStatusRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyDisplayOrderRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyRegisterRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyResponse;
import com.gnagnoohc.scms.domain.competency.dto.CompetencySummary;
import com.gnagnoohc.scms.domain.competency.service.CompetencyQueryService;
import com.gnagnoohc.scms.domain.competency.service.CompetencyService;
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

@Tag(name = "Competency", description = "핵심역량 관리")
@RestController
@RequestMapping("/api/admin/competencies")
@RequiredArgsConstructor
public class CompetencyController {

    private final CompetencyService competencyService;
    private final CompetencyQueryService competencyQueryService;

    // 다른 화면·도메인이 공통으로 참조하는 핵심역량 선택 목록.
    // 지금은 비교과 프로그램 등록 폼, 마일리지 활동 유형 등록 폼이 이 목록으로 competency_id를 고른다.
    // 관리자 전용 경로(/api/admin/**)라 학생 화면에서는 호출할 수 없다.
    @Operation(summary = "핵심역량 목록 조회", description = "활성 최상위 핵심역량을 축순서대로 조회합니다. "
            + "비교과 프로그램 등록·마일리지 활동유형 등록 등 관리자 폼의 핵심역량 드롭다운에서 공용으로 사용합니다.")
    @GetMapping
    public ApiResponse<List<CompetencySummary>> listCompetencies() {
        return ApiResponse.ok(competencyQueryService.getActiveTopLevelCompetencies());
    }

    @Operation(summary = "핵심역량 등록", description = "최상위 핵심역량을 등록합니다. 역량코드는 C100~C600로 자동채번됩니다.")
    @PostMapping
    public ApiResponse<CompetencyResponse> registerCompetency(
            @Valid @RequestBody CompetencyRegisterRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(competencyService.registerCompetency(request, authUser.getId()));
    }

    @Operation(summary = "축순서 지정", description = "핵심역량의 축순서(결과 화면 방사형 차트에 표시될 위치)를 지정합니다. 이미 다른 역량이 쓰고 있는 순서면 그 역량과 서로 교환됩니다.")
    @PatchMapping("/{competencyId}/display-order")
    public ApiResponse<List<CompetencyResponse>> changeDisplayOrder(
            @PathVariable Integer competencyId,
            @Valid @RequestBody CompetencyDisplayOrderRequest request
    ) {
        return ApiResponse.ok(competencyService.changeDisplayOrder(competencyId, request));
    }

    @Operation(summary = "사용여부 관리", description = "핵심역량의 활성/비활성 상태를 전환합니다. 응답 이력이 있는 역량도 삭제 대신 비활성 처리로 과거 기록을 보존합니다.")
    @PatchMapping("/{competencyId}/active-status")
    public ApiResponse<CompetencyResponse> changeActiveStatus(
            @PathVariable Integer competencyId,
            @Valid @RequestBody CompetencyActiveStatusRequest request
    ) {
        return ApiResponse.ok(competencyService.changeActiveStatus(competencyId, request));
    }
}
