package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.CompetencyActiveStatusRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyDisplayOrderRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyRegisterRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyResponse;
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

    @Operation(summary = "핵심역량 관리 목록 조회", description = "교직원 핵심역량 관리 화면용 목록입니다. 비활성 역량까지 축순서대로 모두 내려주며, "
            + "영문명·설명·사용여부를 포함합니다. (활성 역량만 주는 공용 드롭다운 목록은 GET /api/competencies)")
    @GetMapping
    public ApiResponse<List<CompetencyResponse>> getCompetencies() {
        return ApiResponse.ok(competencyService.getCompetenciesForManagement());
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
