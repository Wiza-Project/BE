package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;

// 마일리지 활동 유형 응답 DTO. 정책 등록 화면의 활동 유형 드롭다운용.
public record MileageActivityTypeResponseDTO(
        Integer activityTypeId,
        String activityCode,
        String activityName,
        String categoryCode,
        String earningRoute
) {
    public static MileageActivityTypeResponseDTO from(MileageActivityType activityType) {
        return new MileageActivityTypeResponseDTO(
                activityType.getActivityTypeId(),
                activityType.getActivityCode(),
                activityType.getActivityName(),
                activityType.getCategoryCode(),
                activityType.getEarningRoute()
        );
    }
}
