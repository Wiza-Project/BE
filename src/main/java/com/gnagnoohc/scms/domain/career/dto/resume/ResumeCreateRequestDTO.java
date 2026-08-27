package com.gnagnoohc.scms.domain.career.dto.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이력서 최초 작성 요청. */
@Getter
@NoArgsConstructor
@Schema(description = "이력서 최초 작성 요청 DTO")
public class ResumeCreateRequestDTO {

    @NotBlank(message = "이력서 제목은 필수입니다.")
    @Schema(example = "2026 하반기 이력서")
    private String documentTitle;

    @Valid
    @Schema(description = "고정 이력서 템플릿 본문")
    private ResumeContentDTO contentData;
}
