package com.gnagnoohc.scms.domain.program.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompletionStatus {
    COMPLETED("이수완료"),
    FAILED("이수실패");

    private final String label;
}
