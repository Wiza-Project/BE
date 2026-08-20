package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.request.ProgramAttendanceRecordRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAttendanceQrTokenResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAttendanceResponseDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramAttendanceService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ProgramAttendanceAdmin", description = "운영부서의 비교과 프로그램 출석 기록")
@RestController
@RequestMapping("/api/admin/programs/{programId}/sessions/{sessionId}/attendances")
@RequiredArgsConstructor
public class ProgramAttendanceAdminController {

    private final ProgramAttendanceService programAttendanceService;

    @Operation(summary = "출석 기록", description = "특정 회차에 대해 학생 한 명의 출석 여부를 기록합니다. 이미 기록이 있으면 정정합니다.")
    @PutMapping("/{applicationId}")
    public ApiResponse<ProgramAttendanceResponseDTO> recordAttendance(
            @PathVariable Integer programId,
            @PathVariable Integer sessionId,
            @PathVariable Integer applicationId,
            @Valid @RequestBody ProgramAttendanceRecordRequestDTO request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programAttendanceService.recordAttendance(
                programId, sessionId, applicationId, request, authUser.getId()));
    }

    @Operation(summary = "회차별 출석 목록 조회", description = "특정 회차에 기록된 출석 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<ProgramAttendanceResponseDTO>> listAttendance(
            @PathVariable Integer programId,
            @PathVariable Integer sessionId) {
        return ApiResponse.ok(programAttendanceService.listAttendance(programId, sessionId));
    }

    @Operation(summary = "QR 출석체크 토큰 발급", description = "학생이 스캔할 QR용 단기 토큰을 발급합니다(5분 유효). 발급된 문자열을 프론트가 QR 이미지로 렌더링합니다.")
    @GetMapping("/qr-token")
    public ApiResponse<ProgramAttendanceQrTokenResponseDTO> issueQrToken(
            @PathVariable Integer programId,
            @PathVariable Integer sessionId) {
        return ApiResponse.ok(programAttendanceService.issueQrToken(programId, sessionId));
    }
}
