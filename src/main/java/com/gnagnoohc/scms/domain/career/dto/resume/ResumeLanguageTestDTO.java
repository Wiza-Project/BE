package com.gnagnoohc.scms.domain.career.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 학생이 직접 입력하는 어학성적 한 건. */
public record ResumeLanguageTestDTO(

        @NotBlank(message = "시험명은 필수입니다.")
        @Size(max = 100)
        String testName,

        @Size(max = 20)
        String score,

        LocalDate acquiredDate
) {
}
