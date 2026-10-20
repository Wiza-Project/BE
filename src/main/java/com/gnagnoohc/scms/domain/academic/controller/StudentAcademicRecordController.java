package com.gnagnoohc.scms.domain.academic.controller;

import com.gnagnoohc.scms.domain.academic.dto.StudentAcademicRecordResponse;
import com.gnagnoohc.scms.domain.academic.service.AcademicRecordService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 본인 학적 조회. {@code SecurityConfig}가 {@code /api/students/**}를 이미
 * STUDENT role로 막고 있고, 여기 {@code @PreAuthorize}는 보조 안전장치일 뿐이다.
 *
 * <p>보호자 연락처 수정(PATCH)은 이번 티켓 범위 밖이다 — 후속 티켓.</p>
 */
@Tag(name = "StudentAcademicRecord", description = "학생 본인 학적 조회")
@RestController
@RequestMapping("/api/students/academic-record")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentAcademicRecordController {

    private final AcademicRecordService academicRecordService;

    @Operation(summary = "내 학적 정보 조회", description = "신상정보 + 학적변동목록. 학적 상세를 아직 입력하지 않은 학생도 " +
            "404가 아니라 관련 필드가 null인 응답을 받는다.")
    @GetMapping
    public ApiResponse<StudentAcademicRecordResponse> getMyRecord(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(academicRecordService.getMyRecord(authUser.getId()));
    }
}
