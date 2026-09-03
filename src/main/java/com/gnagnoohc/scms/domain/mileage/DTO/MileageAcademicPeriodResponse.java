package com.gnagnoohc.scms.domain.mileage.DTO;

/** 오늘 날짜 기준으로 서버가 판별한 현재 학년도/학기 응답이다. */
public record MileageAcademicPeriodResponse(
        Integer academicYear,
        String semesterCode
) {
}
