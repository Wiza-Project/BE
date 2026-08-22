package com.gnagnoohc.scms.domain.counsel.dto;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;

/**
 * 상담 유형 조회 API가 외부에 공개하는 필드만 정의한 응답 DTO다.
 *
 * <p>엔티티를 직접 반환하지 않아 활성 여부, 생성자 같은 내부 필드가 응답에 우연히 노출되는 것을 막는다.
 * 유형 식별자({@code counselingTypeId})는 일정 조회 경로
 * {@code /api/students/counseling-types/{counselingTypeId}/schedules}의 PathVariable로 쓰이므로 의도적으로 포함한다.</p>
 */
public record CounselingTypeResponse(
        Integer counselingTypeId,
        String typeCode,
        String typeName,
        String applicationRoute,
        String counselingMethod,
        String precedingProcedure
) {
    /**
     * 영속성 엔티티에서 조회 API 계약에 포함된 값만 선택해 응답 DTO를 만든다.
     */
    public static CounselingTypeResponse from(CounselingType counselingType) {
        return new CounselingTypeResponse(
                counselingType.getCounselingTypeId(),
                counselingType.getTypeCode(),
                counselingType.getTypeName(),
                counselingType.getApplicationRoute(),
                counselingType.getCounselingMethod(),
                counselingType.getPrecedingProcedure()
        );
    }
}
