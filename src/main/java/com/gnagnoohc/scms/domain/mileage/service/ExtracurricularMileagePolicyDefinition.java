package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;

import java.util.List;

/** 비교과 프로그램 유형별 마일리지 정책의 고정된 업무 식별값을 한 곳에서 관리한다. */
public final class ExtracurricularMileagePolicyDefinition {

    public static final List<String> PROGRAM_TYPE_CODES =
            List.of("PT100", "PT200", "PT300", "PT400", "PT500", "PT600");
    public static final String ACTIVITY_CODE_PREFIX = "EXTRACURRICULAR_";
    public static final String CATEGORY_CODE = "EXTRACURRICULAR";
    public static final String EARNING_ROUTE = "PROGRAM_COMPLETION";

    private ExtracurricularMileagePolicyDefinition() {
    }

    public static String activityCodeForProgramType(String programTypeCode) {
        return ACTIVITY_CODE_PREFIX + programTypeCode;
    }

    public static boolean isExtracurricular(MileageActivityType activityType) {
        return activityType != null
                && CATEGORY_CODE.equalsIgnoreCase(activityType.getCategoryCode())
                && EARNING_ROUTE.equalsIgnoreCase(activityType.getEarningRoute());
    }

    public static boolean isProgramTypePolicy(MileageActivityType activityType) {
        return isExtracurricular(activityType)
                && activityType.getProgramTypeCode() != null
                && PROGRAM_TYPE_CODES.contains(activityType.getProgramTypeCode().getCode());
    }
}
