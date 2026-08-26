package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramSessionUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramSessionResponseDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramSessionService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ProgramSessionAdmin", description = "운영부서의 비교과 프로그램 회차 관리")
@RestController
@RequestMapping("/api/admin/programs/{programId}/sessions")
@RequiredArgsConstructor
public class ProgramSessionAdminController {

    private final ProgramSessionService programSessionService;

    @Operation(summary = "회차 등록", description = "프로그램의 회차(교육 일정)를 등록합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProgramSessionResponseDTO> registerSession(
            @PathVariable Integer programId,
            @Valid @RequestBody ProgramSessionRegisterRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programSessionService.registerSession(programId, request, authUser.getId()));
    }

    @Operation(summary = "회차 목록 조회", description = "프로그램에 등록된 회차 목록을 회차 번호 순으로 조회합니다.")
    @GetMapping
    public ApiResponse<List<ProgramSessionResponseDTO>> listSessions(@PathVariable Integer programId) {
        return ApiResponse.ok(programSessionService.listSessions(programId));
    }

    @Operation(summary = "회차 수정", description = "프로그램의 회차(장소 포함)를 수정합니다.")
    @PutMapping("/{sessionId}")
    public ApiResponse<ProgramSessionResponseDTO> updateSession(
            @PathVariable Integer programId,
            @PathVariable Integer sessionId,
            @Valid @RequestBody ProgramSessionUpdateRequestDTO request) {
        return ApiResponse.ok(programSessionService.updateSession(programId, sessionId, request));
    }
}
