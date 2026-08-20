package com.gnagnoohc.scms.domain.competency.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Competency", description = "핵심역량 관리")
@RestController
@RequestMapping("/api/admin/competencies")
@RequiredArgsConstructor
public class CompetencyController {

    private final CompetencyService competencyService;

    @Operation(summary = "핵심역량 등록", description = "최상위 핵심역량을 등록합니다. 역량코드는 C1~C6로 자동채번됩니다.")
    @PostMapping
    public ApiResponse<CompetencyResponse> registerCompetency(
            @Valid @RequestBody CompetencyRegisterRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(competencyService.registerCompetency(request, authUser.getId()));
    }
}
