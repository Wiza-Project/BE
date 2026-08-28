package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;

import java.math.BigDecimal;
import java.util.List;

/** 비교과 마일리지 정책의 고정된 업무 식별값을 한 곳에서 관리한다. */
public final class ExtracurricularMileagePolicyDefinition {

    public static final List<String> CORE_COMPETENCY_CODES =
            List.of("C100", "C200", "C300", "C400", "C500", "C600");
    public static final String ACTIVITY_CODE_PREFIX = "EXTRACURRICULAR_";
    public static final String CATEGORY_CODE = "EXTRACURRICULAR";
    public static final String EARNING_ROUTE = "PROGRAM_COMPLETION";
    public static final BigDecimal POINTS = new BigDecimal("5.00");

    private ExtracurricularMileagePolicyDefinition() {
    }

    public static String activityCodeFor(String competencyCode) {
        return ACTIVITY_CODE_PREFIX + competencyCode;
    }

    public static boolean isExtracurricular(MileageActivityType activityType) {
        return activityType != null
                && CATEGORY_CODE.equalsIgnoreCase(activityType.getCategoryCode())
                && EARNING_ROUTE.equalsIgnoreCase(activityType.getEarningRoute());
    }
}
