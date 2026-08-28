package com.gnagnoohc.scms.domain.career.dto.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기존 이력서 버전의 임시 저장용 수정 요청. 확정 저장은 새 버전을 만든다. */
@Getter
@NoArgsConstructor
@Schema(description = "이력서 수정 요청 DTO")
public class ResumeUpdateRequestDTO {

    @NotBlank(message = "이력서 제목은 필수입니다.")
    @Size(max = 200, message = "이력서 제목은 200자 이하여야 합니다.")
    private String documentTitle;

    @Valid
    private ResumeContentDTO contentData;
}
