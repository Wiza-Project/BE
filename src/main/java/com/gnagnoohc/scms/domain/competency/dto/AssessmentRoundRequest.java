package com.gnagnoohc.scms.domain.competency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record AssessmentRoundRequest(
        @NotBlank(message = "진단명은 필수입니다.")
        @Size(max = 200, message = "진단명은 200자를 초과할 수 없습니다.") String assessmentName,
        @NotNull(message = "학년도는 필수입니다.") Integer academicYear,
        @NotBlank(message = "학기 구분은 필수입니다.")
        @Size(max = 20, message = "학기 구분은 20자를 초과할 수 없습니다.") String semesterCode,
        @NotBlank(message = "진단구분(사전/사후)은 필수입니다.")
        @Pattern(regexp = "PRE|POST", message = "진단구분은 PRE 또는 POST여야 합니다.") String assessmentType,
        @NotNull(message = "응시 시작일시는 필수입니다.") Instant startsAt,
        @NotNull(message = "응시 종료일시는 필수입니다.") Instant endsAt,
        // NULL이면 전체 학생 대상. 빈 객체({})는 허용하지 않는다(둘의 혼용 금지).
        @Size(min = 1, message = "응시 대상 조건은 비어 있을 수 없습니다.") Map<String, Object> targetCondition
) {}
