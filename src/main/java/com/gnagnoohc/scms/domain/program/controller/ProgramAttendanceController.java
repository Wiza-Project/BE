package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramAttendanceCheckInRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAttendanceResponseDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramAttendanceService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ProgramAttendance", description = "학생의 비교과 프로그램 QR 자기출석체크")
@RestController
@RequestMapping("/api/students/programs")
@RequiredArgsConstructor
public class ProgramAttendanceController {

    private final ProgramAttendanceService programAttendanceService;

    @Operation(summary = "QR 출석체크", description = "스태프 화면에 뜬 QR을 스캔해 본인 출석을 스스로 기록합니다. 기존 스태프 수동 출석 입력과 공존합니다.")
    @PostMapping("/{programId}/sessions/{sessionId}/attendances/check-in")
    public ApiResponse<ProgramAttendanceResponseDTO> checkInWithQr(
            @PathVariable Integer programId,
            @PathVariable Integer sessionId,
            @Valid @RequestBody ProgramAttendanceCheckInRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programAttendanceService.checkInWithQr(
                programId, sessionId, request.token(), authUser.getId()));
    }
}
