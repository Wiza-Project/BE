package com.gnagnoohc.scms.domain.competency.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssessmentQuestionEditRequest(
        @NotBlank(message = "문항 내용은 필수입니다.") String questionText,
        @NotNull(message = "역문항 여부는 필수입니다.") Boolean reverse,
        @NotEmpty(message = "응답옵션은 최소 1개 이상이어야 합니다.") @Valid List<ResponseOption> responseOptions
) {
    public record ResponseOption(
            @NotNull(message = "응답옵션 값은 필수입니다.") Integer value,
            @NotBlank(message = "응답옵션 라벨은 필수입니다.") String label
    ) {}
}
