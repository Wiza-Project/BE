package com.gnagnoohc.scms.domain.academic.dto;

import java.time.LocalDate;

/**
 * 교직원 학생 목록(GET /api/staff/students) 한 행.
 *
 * <p>{@code userId}는 상세 조회(GET /api/staff/students/{studentId}) 호출에 쓰는 내부 PK고,
 * {@code studentId}는 화면에 보여주는 학번({@code app_user.university_no})이다 — 이름이
 * 비슷해 헷갈리기 쉬워 둘 다 명시적으로 내려준다.</p>
 *
 * <p>{@code admissionDate}는 목록 조회 1번 + 페이지 학생들의 입학(AC100) 변동행 in절 조회
 * 1번, 총 쿼리 2번으로 채운다({@link com.gnagnoohc.scms.domain.academic.repository.AcademicRecordQueryRepository}
 * 참고) — 행마다 따로 조회하면 N+1이 터진다(주의사항 7).</p>
 */
public record AdminStudentListItemResponse(
        Integer userId,
        String studentId,
        String name,
        String phone,
        String email,
        String status,
        String majorCode,
        String majorName,
        Integer grade,
        LocalDate admissionDate
) {
    /** {@link com.gnagnoohc.scms.domain.academic.repository.AcademicRecordQueryRepository}가 만든 행에 입학일자만 채워 넣는다. */
    public AdminStudentListItemResponse withAdmissionDate(LocalDate admissionDate) {
        return new AdminStudentListItemResponse(
                userId, studentId, name, phone, email, status, majorCode, majorName, grade, admissionDate);
    }
}
