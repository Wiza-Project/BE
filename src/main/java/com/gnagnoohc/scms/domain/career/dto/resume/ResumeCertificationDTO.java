package com.gnagnoohc.scms.domain.career.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 학생이 직접 입력하는 자격증 한 건. */
public record ResumeCertificationDTO(

        @NotBlank(message = "자격증명은 필수입니다.")
        @Size(max = 100)
        String certificationName,

        @Size(max = 100)
        String issuer,

        LocalDate acquiredDate
) {
}
