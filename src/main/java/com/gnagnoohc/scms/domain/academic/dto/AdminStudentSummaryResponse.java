package com.gnagnoohc.scms.domain.academic.dto;

import java.util.Map;

/**
 * 교직원 학생 목록 상단 통계 타일(GET /api/staff/students/summary).
 * {@code byStatus}의 key는 {@code app_user.academic_status}에 실제로 쓰는 5개 라벨
 * (재학/휴학/졸업/제적/자퇴) — 데이터가 0건인 상태도 0으로 채워 내려준다(FE가 매 상태
 * 타일을 항상 그릴 수 있게).
 */
public record AdminStudentSummaryResponse(
        long total,
        Map<String, Long> byStatus
) {
}
