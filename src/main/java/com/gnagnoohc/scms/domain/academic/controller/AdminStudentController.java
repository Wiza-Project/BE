package com.gnagnoohc.scms.domain.academic.controller;

import com.gnagnoohc.scms.domain.academic.dto.AdminStudentListItemResponse;
import com.gnagnoohc.scms.domain.academic.dto.AdminStudentSearchConditionDTO;
import com.gnagnoohc.scms.domain.academic.dto.AdminStudentSummaryResponse;
import com.gnagnoohc.scms.domain.academic.dto.StudentAcademicRecordResponse;
import com.gnagnoohc.scms.domain.academic.service.AcademicRecordService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 교직원 학적조회(학적부관리) 화면. {@code SecurityConfig}가 {@code /api/admin/**}를
 * 이미 STAFF role로 막고 있고, 여기 {@code @PreAuthorize}는 보조 안전장치일 뿐이다.
 *
 * <p>교직원 수정 API, 변동이력 입력 화면, 파일 업로드, AuditLog 기록은 전부 이번 티켓
 * 범위 밖이다 — 조회 전용.</p>
 */
@Tag(name = "AdminStudent", description = "교직원 학적조회(학적부관리)")
@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STAFF')")
public class AdminStudentController {

    private final AcademicRecordService academicRecordService;

    @Operation(summary = "학생 목록 조회", description = "필터: majorCodeId(MAJOR 공통코드 식별자), grade, status, keyword(학번/이름).")
    @GetMapping
    public ApiResponse<PageResponse<AdminStudentListItemResponse>> list(
            @ModelAttribute AdminStudentSearchConditionDTO condition,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(academicRecordService.listStudents(condition, pageable));
    }

    @Operation(summary = "학적상태 요약", description = "상단 통계 타일용 {total, byStatus}. 목록이 페이지네이션이라 전체 카운트를 별도로 뗀다.")
    @GetMapping("/summary")
    public ApiResponse<AdminStudentSummaryResponse> summary() {
        return ApiResponse.ok(academicRecordService.getSummary());
    }

    @Operation(summary = "학생 상세 조회(학적부관리 모달)", description = "studentId는 app_user.user_id(내부 PK)다.")
    @GetMapping("/{studentId}")
    public ApiResponse<StudentAcademicRecordResponse> detail(@PathVariable Integer studentId) {
        return ApiResponse.ok(academicRecordService.getStudentDetail(studentId));
    }
}
