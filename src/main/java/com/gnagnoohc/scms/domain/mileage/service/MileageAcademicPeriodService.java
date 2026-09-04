package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageAcademicPeriodResponse;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        return resolvePeriod(LocalDate.now(DateTimeUtils.KST_ZONE));
    }

    /** 임의 날짜가 속한 학년도/학기를 판별한다. 학기 경계는 resolveCurrentPeriod()와 동일하다. */
    public MileageAcademicPeriodResponse resolvePeriod(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now(DateTimeUtils.KST_ZONE) : date;
        int year = resolveAcademicYear(targetDate);
        int monthDay = targetDate.getMonthValue() * 100 + targetDate.getDayOfMonth();

        if (monthDay >= 302 && monthDay <= 831) {
            return new MileageAcademicPeriodResponse(year, SPRING);
        }
        return new MileageAcademicPeriodResponse(year, FALL);
    }

    /** 한국 시간 기준 날짜가 속한 학년도를 계산한다. 학년도는 매년 3월 2일에 시작한다. */
    public int resolveAcademicYear(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now(DateTimeUtils.KST_ZONE) : date;
        int monthDay = targetDate.getMonthValue() * 100 + targetDate.getDayOfMonth();
        return monthDay >= 302 ? targetDate.getYear() : targetDate.getYear() - 1;
    }

    /** 거래 시각을 한국 시간으로 변환해 해당 학년도를 계산한다. */
    public int resolveAcademicYear(Instant occurredAt) {
        LocalDate date = occurredAt == null
                ? LocalDate.now(DateTimeUtils.KST_ZONE)
                : occurredAt.atZone(DateTimeUtils.KST_ZONE).toLocalDate();
        return resolveAcademicYear(date);
    }

    /** 학년도 조회에 사용할 [시작일시, 다음 학년도 시작일시) 범위를 반환한다. */
    public AcademicYearBounds resolveAcademicYearBounds(int academicYear) {
        Instant startAt = LocalDate.of(academicYear, 3, 2)
                .atStartOfDay(DateTimeUtils.KST_ZONE)
                .toInstant();
        Instant endAt = LocalDate.of(academicYear + 1, 3, 2)
                .atStartOfDay(DateTimeUtils.KST_ZONE)
                .toInstant();
        return new AcademicYearBounds(startAt, endAt);
    }

    public record AcademicYearBounds(Instant startAt, Instant endAt) {
    }
}
