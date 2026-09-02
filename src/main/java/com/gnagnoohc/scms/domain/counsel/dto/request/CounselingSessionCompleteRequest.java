package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 회기 출결 완료 요청. attendanceStatus 값 자체의 유효성(PRESENT/ABSENT/NO_SHOW)은 엔티티가 검증한다. */
public record CounselingSessionCompleteRequest(
        @NotNull String attendanceStatus,
        Instant nextSessionAt
) {
}
