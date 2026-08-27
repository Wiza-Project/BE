package com.gnagnoohc.scms.domain.career.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 이력서 경력사항 한 건. */
public record ResumeCareerDTO(

        @NotBlank(message = "회사명은 필수입니다.")
        @Size(max = 100)
        String companyName,

        @Size(max = 100)
        String position,

        LocalDate startDate,

        LocalDate endDate,

        @Size(max = 500)
        String description
) {
}
