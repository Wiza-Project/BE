package com.gnagnoohc.scms.domain.program.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {
    /** 정원 내 신청. */
    APPLIED("신청완료"),
    /** 정원 초과로 대기순번이 부여된 신청. */
    WAITLISTED("대기"),
    /** 운영부서가 참여를 승인한 신청. */
    APPROVED("승인"),
    /** 운영부서가 참여를 반려한 신청. */
    REJECTED("반려"),
    /** 학생이 스스로 취소한 신청. */
    CANCELLED("취소");

    private final String label;
}
