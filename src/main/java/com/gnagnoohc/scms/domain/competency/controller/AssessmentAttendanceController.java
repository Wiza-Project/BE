package com.gnagnoohc.scms.domain.competency.controller;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentAttendanceResponse;
import com.gnagnoohc.scms.domain.competency.service.AssessmentAttendanceService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssessmentAttendance", description = "진단 응시현황 조회")
@RestController
@RequestMapping("/api/admin/assessment-rounds")
@RequiredArgsConstructor
public class AssessmentAttendanceController {

    private final AssessmentAttendanceService assessmentAttendanceService;

    @Operation(summary = "응시율 조회", description = "회차의 응시 대상자 수 대비 완료 건수를 실시간 집계합니다. "
            + "targetCondition이 없는 회차는 전체 학생을 대상자로 집계합니다.")
    @GetMapping("/{roundId}/attendance")
    public ApiResponse<AssessmentAttendanceResponse> getAttendance(@PathVariable Integer roundId) {
        return ApiResponse.ok(assessmentAttendanceService.getAttendance(roundId));
    }
}
