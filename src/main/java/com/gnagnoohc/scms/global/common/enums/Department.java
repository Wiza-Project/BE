package com.gnagnoohc.scms.global.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로세스 모델의 스윔레인(부서)을 그대로 옮긴 것입니다.
 * UserType.STAFF 인 사용자가 어느 레인에 속하는지를 나타냅니다.
 *
 * 주의: 이 enum 은 "조직도"가 아니라 "권한 경계"입니다.
 * 실제 학교 조직 개편에 따라 부서가 늘어날 수 있으므로,
 * 권한 체크를 부서명이 아니라 아래 boolean 메서드로 하세요.
 */
@Getter
@RequiredArgsConstructor
public enum Department {

    STUDENT_COMPETENCY_CENTER("학생역량센터"),   // 분류체계 관리/승인, 핵심역량·진단문항, 마일리지 항목
    CAREER_COUNSELING_CENTER("진로심리상담센터"), // 상담기준정보, 상담사 등록
    EMPLOYMENT_SUPPORT_CENTER("취업지원센터"),
    ENGINEERING_INNOVATION_CENTER("공학교육혁신센터"),
    STUDENT_SUPPORT_CENTER("학생지원센터"),
    ACADEMIC_DEPARTMENT("학과/학부"),
    CAREER_SUPPORT_OFFICE("취창업지원과"),        // 구인공고, 잡매칭, 취업통계
    NON_SUBJECT_OPERATION("비교과운영부서");      // 참여승인, 결과등록, 마일리지 실적등록

    private final String label;

    /** 비교과 프로그램 분류체계를 최종 승인할 수 있는 부서인가 (P1100) */
    public boolean canApproveProgramCategory() {
        return this == STUDENT_COMPETENCY_CENTER;
    }

    /** 비교과 프로그램을 신청(개설 요청)할 수 있는 부서인가 (P1100) */
    public boolean canRequestProgram() {
        return this != NON_SUBJECT_OPERATION;
    }

    /** 프로그램 참여승인·결과등록 권한이 있는 부서인가 (P1200) */
    public boolean canOperateProgram() {
        return this == NON_SUBJECT_OPERATION || this == STUDENT_COMPETENCY_CENTER;
    }

    /** 취창업 업무 담당 부서인가 (P5100) */
    public boolean canManageCareer() {
        return this == CAREER_SUPPORT_OFFICE || this == EMPLOYMENT_SUPPORT_CENTER;
    }
}
