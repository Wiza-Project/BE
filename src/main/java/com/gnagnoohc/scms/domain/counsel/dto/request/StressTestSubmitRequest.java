package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 제출 요청. studentId·totalScore·resultLevel 같은 필드는 이 레코드에 아예 없으므로
 * 클라이언트가 함께 보내더라도(Jackson 미지 필드 무시 기본 정책) 서버가 읽을 방법이 없다.
 * 문항 수(11) 자체는 여기서 상수로 고정하지 않는다 — 현재 버전 문항 집합과 정확히 같은지는
 * StressTestService가 DB 조회 결과와 대조해 최종 검사한다.
 */
public record StressTestSubmitRequest(
        @NotBlank String testVersion,
        @NotNull @Size(min = 11, max = 11) List<@NotNull @Valid Answer> answers
) {
    public record Answer(
            @NotNull @Positive Long questionId,
            @NotNull @Min(0) @Max(3) Integer selectedValue
    ) {
    }
}
