package com.gnagnoohc.scms.domain.program.dto.program;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;

import java.math.BigDecimal;

// 프로그램 등록/수정 폼에서 programTypeCodeId 선택 시 매핑되는 마일리지 정책을 실시간으로 미리보기 위한 응답.
// 매핑되는 정책이 없으면(시드 데이터 미존재, 유효기간 밖 등) mileagePolicyId 이하 필드는 null로 내려간다.
public record ProgramMileagePolicyPreviewResponseDTO(
        Integer programTypeCodeId,
        Integer mileagePolicyId,
        BigDecimal mileagePoints,
        String mileageActivityName,
        Integer academicYear,
        String semesterCode
) {

    public static ProgramMileagePolicyPreviewResponseDTO from(Integer programTypeCodeId, MileagePolicy policy) {
        if (policy == null) {
            return new ProgramMileagePolicyPreviewResponseDTO(programTypeCodeId, null, null, null, null, null);
        }
        return new ProgramMileagePolicyPreviewResponseDTO(
                programTypeCodeId,
                policy.getMileagePolicyId(),
                policy.getPoints(),
                policy.getActivityType().getActivityName(),
                policy.getAcademicYear(),
                policy.getSemesterCode()
        );
    }
}
