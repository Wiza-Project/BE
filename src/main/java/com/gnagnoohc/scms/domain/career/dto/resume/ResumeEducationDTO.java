package com.gnagnoohc.scms.domain.career.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 이력서 학력사항 한 건. */
public record ResumeEducationDTO(

        @NotBlank(message = "학교명은 필수입니다.")
        @Size(max = 100)
        String schoolName,

        @Size(max = 100)
        String major,

        LocalDate admissionDate,

        LocalDate graduationDate,

        @Size(max = 20)
        String enrollmentStatus
) {
}
