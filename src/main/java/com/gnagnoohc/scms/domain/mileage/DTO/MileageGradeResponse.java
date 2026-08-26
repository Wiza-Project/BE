package com.gnagnoohc.scms.domain.mileage.DTO;

import java.math.BigDecimal;

/** 학생의 누적 마일리지와 현재·다음 등급을 담는 조회 전용 응답이다. */
public record MileageGradeResponse(
        BigDecimal cumulativePoints,
        Grade currentGrade,
        Grade nextGrade,
        BigDecimal pointsToNextGrade
) {

    /** 등급 정책의 화면 노출용 정보다. */
    public record Grade(
            Integer gradePolicyId,
            String gradeName,
            BigDecimal minimumPoints,
            String semesterCode
    ) {
    }
}
