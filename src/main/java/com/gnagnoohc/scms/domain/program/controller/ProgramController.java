package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.CompetencyOptionResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramRegisterResponseDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Program", description = "비교과프로그램 등록")
@RestController
@RequestMapping("/api/admin/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @Operation(summary = "프로그램 등록", description = "비교과 프로그램을 신규 등록합니다 (DRAFT 상태)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProgramRegisterResponseDTO> register(
            @Valid @RequestBody ProgramRegisterRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programService.register(request, authUser.getId()));
    }

    @Operation(summary = "핵심역량 옵션 조회", description = "프로그램 등록 폼에서 사용할 핵심역량 목록을 조회합니다")
    @GetMapping("/competencies")
    public ApiResponse<List<CompetencyOptionResponseDTO>> listCompetencyOptions() {
        return ApiResponse.ok(programService.getCompetencyOptions());
    }
}
