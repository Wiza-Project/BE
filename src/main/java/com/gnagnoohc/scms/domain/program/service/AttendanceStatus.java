package com.gnagnoohc.scms.domain.program.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttendanceStatus {
    PRESENT("출석"),
    ABSENT("결석");

    private final String label;
}
