package com.gnagnoohc.scms.domain.competency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;

public record AssessmentRoundRequest(
        @NotBlank(message = "진단명은 필수입니다.") String assessmentName,
        @NotNull(message = "학년도는 필수입니다.") Integer academicYear,
        @NotBlank(message = "학기 구분은 필수입니다.") String semesterCode,
        @NotBlank(message = "진단구분(사전/사후)은 필수입니다.")
        @Pattern(regexp = "PRE|POST", message = "진단구분은 PRE 또는 POST여야 합니다.") String assessmentType,
        @NotNull(message = "응시 시작일시는 필수입니다.") Instant startsAt,
        @NotNull(message = "응시 종료일시는 필수입니다.") Instant endsAt,
        // NULL이면 전체 학생 대상. 빈 객체({})와 혼용하지 않는다.
        Map<String, Object> targetCondition
) {}
