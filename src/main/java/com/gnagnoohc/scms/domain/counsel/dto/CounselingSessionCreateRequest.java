package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 후속 회기 생성 요청. 시간 범위·중복 검증은 서비스가 수행한다. */
public record CounselingSessionCreateRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
