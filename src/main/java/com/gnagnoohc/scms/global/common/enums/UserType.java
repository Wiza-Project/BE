package com.gnagnoohc.scms.global.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로세스 모델에 등장하는 액터를 4종으로 정리한 것입니다.
 *
 *  STUDENT   학생        - 프로그램 참여신청, 진단검사, 상담예약, 마일리지 신청, 포트폴리오 등록
 *  STAFF     교직원      - 학생역량센터/비교과운영부서/취창업지원과/학과 등. 소속은 Department 로 구분
 *  COUNSELOR 상담사      - 상담일정 등록, 상담 확정, 상담결과 등록 (P3100)
 *  COMPANY   기업체      - 구인신청 (P5100)
 *
 * 권한은 UserType 만으로 부족합니다. 같은 STAFF 라도 학생역량센터와 비교과운영부서가
 * 할 수 있는 일이 다르므로 Department 를 함께 검사해야 합니다.
 */
@Getter
@RequiredArgsConstructor
public enum UserType {

    STUDENT("학생"),
    STAFF("교직원"),
    COUNSELOR("상담사"),
    COMPANY("기업체");

    private final String label;

    /** Spring Security 권한 문자열 (ROLE_ 접두사 규칙) */
    public String toAuthority() {
        return "ROLE_" + name();
    }
}
