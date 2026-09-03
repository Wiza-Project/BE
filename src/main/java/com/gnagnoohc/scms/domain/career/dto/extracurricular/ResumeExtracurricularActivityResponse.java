package com.gnagnoohc.scms.domain.career.dto.extracurricular;

import com.gnagnoohc.scms.domain.career.entity.ResumeExtracurricularActivity;

import java.time.Instant;

/** 이력서 화면의 비교과 수료 이력 조회 응답. */
public record ResumeExtracurricularActivityResponse(
        Integer applicationId,
        Integer programId,
        String programName,
        String programTypeCode,
        String programTypeName,
        Integer competencyId,
        String competencyName,
        Instant operationStartedAt,
        Instant operationEndedAt,
        String operatingDepartmentName
) {
    public static ResumeExtracurricularActivityResponse from(ResumeExtracurricularActivity activity) {
        return new ResumeExtracurricularActivityResponse(
                activity.getApplicationId(),
                activity.getProgramId(),
                activity.getProgramName(),
                activity.getProgramTypeCode(),
                activity.getProgramTypeName(),
                activity.getCompetencyId(),
                activity.getCompetencyName(),
                activity.getOperationStartedAt(),
                activity.getOperationEndedAt(),
                activity.getOperatingDepartmentName());
    }
}
