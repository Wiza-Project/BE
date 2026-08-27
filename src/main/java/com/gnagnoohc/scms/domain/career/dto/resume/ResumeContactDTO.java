package com.gnagnoohc.scms.domain.career.dto.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 이력서 기본 인적사항. */
@Schema(description = "이력서 인적사항")
public record ResumeContactDTO(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50)
        String name,

        @Pattern(regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$", message = "올바른 연락처 형식이 아닙니다.")
        @Schema(example = "010-1234-5678")
        String phoneNumber,

        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @Size(max = 200)
        String address
) {
}
