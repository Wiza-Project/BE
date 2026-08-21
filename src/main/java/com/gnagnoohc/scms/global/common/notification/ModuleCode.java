package com.gnagnoohc.scms.global.common.notification;

/**
 * 알림을 발송하는 도메인(모듈) 식별자. NotificationType과 마찬가지로 자유 문자열 대신
 * enum으로 고정해서, 호출부의 오타(예: "Mileage", "COMPETENCE")가 컴파일 시점에 걸리도록 한다.
 * 새 도메인이 추가되면 여기에 값을 추가하고 팀 채널에 공유할 것.
 */
public enum ModuleCode {
    PROGRAM,     // 비교과 프로그램
    COUNSEL,     // 상담
    MILEAGE,     // 마일리지
    CAREER,      // 취창업
    COMPETENCY   // 핵심역량/진단
}
