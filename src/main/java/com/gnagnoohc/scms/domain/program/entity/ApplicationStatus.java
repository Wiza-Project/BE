package com.gnagnoohc.scms.domain.program.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {
    // 정원 내 신청.
    APPLIED("신청완료"),
    // 정원 초과로 대기순번이 부여된 신청.
    WAITLISTED("대기");

    private final String label;
}
