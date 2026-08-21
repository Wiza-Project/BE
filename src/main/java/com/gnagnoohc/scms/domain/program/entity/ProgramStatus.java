package com.gnagnoohc.scms.domain.program.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProgramStatus {
    /**
     * 등록 직후 ~ 모집 마감(recruitmentEndsAt) 전까지의 "모집중" 상태.
     * 이름은 기존 컬럼 기본값("DRAFT")과의 하위 호환을 위해 그대로 유지한다.
     */
    DRAFT("모집중"),
    OPERATING("운영중"),
    CLOSED("종료");

    private final String label;
}
