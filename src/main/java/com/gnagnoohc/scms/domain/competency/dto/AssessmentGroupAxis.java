package com.gnagnoohc.scms.domain.competency.dto;

// SCR-A06 역량별 분포·집단별 비교의 그룹 축. 단과대는 지원하지 않는다(학적 데이터에 단과대
// 계층이 없어 학년·전공(학과) 두 축만 존재 — 개발순서_브랜치.md §3-7 근거).
public enum AssessmentGroupAxis {
    GRADE, MAJOR
}
