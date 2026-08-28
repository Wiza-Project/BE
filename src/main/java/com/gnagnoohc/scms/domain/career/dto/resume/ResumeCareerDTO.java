package com.gnagnoohc.scms.domain.career.dto.resume;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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

    /** 종료일이 입력된 경우 시작일보다 앞설 수 없다. */
    @JsonIgnore
    @AssertTrue(message = "종료일은 시작일보다 빠를 수 없습니다.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
