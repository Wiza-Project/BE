package com.gnagnoohc.scms.domain.mileage.service;

/** 역량진단(사전/사후 진단) 완료 마일리지 정책의 고정된 업무 식별값을 한 곳에서 관리한다. */
public final class CompetencyDiagnosisMileagePolicyDefinition {

    public static final String ACTIVITY_CODE = "COMPETENCY_DIAGNOSIS_COMPLETION";
    public static final String CATEGORY_CODE = "COMPETENCY_DIAGNOSIS";
    public static final String EARNING_ROUTE = "ASSESSMENT_COMPLETION";

    private CompetencyDiagnosisMileagePolicyDefinition() {
    }
}
