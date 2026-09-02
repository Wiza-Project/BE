package com.gnagnoohc.scms.domain.program.controller;

import com.gnagnoohc.scms.domain.program.dto.attendance.ProgramAttendanceResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.attendance.ProgramMyAttendanceResponseDTO;
import com.gnagnoohc.scms.domain.program.service.ProgramAttendanceService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "ProgramAttendance", description = "학생의 비교과 프로그램 출결 조회")
@RestController
@RequestMapping("/api/students/programs")
@RequiredArgsConstructor
public class ProgramAttendanceController {

    private final ProgramAttendanceService programAttendanceService;

    /**
     * 로그인한 학생 본인이 신청한 프로그램의 전체 회차별 출결 현황을 조회한다.
     *
     * @param programId 조회할 프로그램의 PK
     * @param authUser  인증 정보에서 추출한 로그인 사용자 (요청 본인의 신청 건만 조회됨)
     */
    @Operation(summary = "내 출결 조회", description = "본인이 신청한 프로그램의 전체 회차별 출결 현황을 조회합니다. 아직 기록되지 않은 회차는 출결 관련 필드가 null로 내려갑니다.")
    @GetMapping("/{programId}/attendances")
    public ApiResponse<List<ProgramMyAttendanceResponseDTO>> listMyAttendance(
            @PathVariable Integer programId,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(programAttendanceService.listMyAttendance(programId, authUser.getId()));
    }
}
