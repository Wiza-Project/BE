package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageAcademicPeriodResponse;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 오늘 날짜를 기준으로 현재 학년도/학기를 판별한다. 공식 학사력 테이블이 없어 날짜 공식으로
 * 계산하며, FE(academicPeriod.js)가 쓰던 것과 동일한 학기 경계를 서버로 이전한 것이다.
 */
@Service
public class MileageAcademicPeriodService {

    private static final String SPRING = "SPRING";
    private static final String FALL = "FALL";

    /** 1학기: 3/2~8/31, 2학기: 9/1~(익년)3/1. 1/1~3/1은 전년도 2학기로 취급한다. */
    public MileageAcademicPeriodResponse resolveCurrentPeriod() {
        LocalDate today = LocalDate.now(DateTimeUtils.KST_ZONE);
        int year = today.getYear();
        int monthDay = today.getMonthValue() * 100 + today.getDayOfMonth();

        if (monthDay >= 302 && monthDay <= 831) {
            return new MileageAcademicPeriodResponse(year, SPRING);
        }
        if (monthDay >= 901) {
            return new MileageAcademicPeriodResponse(year, FALL);
        }
        return new MileageAcademicPeriodResponse(year - 1, FALL);
    }
}
