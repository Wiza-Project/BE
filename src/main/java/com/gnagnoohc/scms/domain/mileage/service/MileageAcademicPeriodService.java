package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageAcademicPeriodResponse;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 오늘 날짜를 기준으로 현재 학기와 학사 주기 범위를 판별한다. 공식 학사력 테이블이 없어
 * 날짜 공식으로 계산하며, FE(academicPeriod.js)가 쓰던 것과 동일한 학기 경계를 서버로 이전한 것이다.
 */
@Service
public class MileageAcademicPeriodService {

    private static final String SPRING = "SPRING";
    private static final String FALL = "FALL";

    /** 1학기: 3/2~8/31, 2학기: 9/1~(익년)3/1. 1/1~3/1은 이전 주기의 2학기로 취급한다. */
    public MileageAcademicPeriodResponse resolveCurrentPeriod() {
        return resolvePeriod(LocalDate.now(DateTimeUtils.KST_ZONE));
    }

    /** 임의 날짜가 속한 학기와 동일한 경계의 학사 주기를 판별한다. */
    public MileageAcademicPeriodResponse resolvePeriod(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now(DateTimeUtils.KST_ZONE) : date;
        int monthDay = targetDate.getMonthValue() * 100 + targetDate.getDayOfMonth();

        if (monthDay >= 302 && monthDay <= 831) {
            return new MileageAcademicPeriodResponse(SPRING);
        }
        return new MileageAcademicPeriodResponse(FALL);
    }

    /** 현재 날짜가 속한 한 주기의 [시작일시, 다음 주기 시작일시) 범위를 반환한다. */
    public PeriodBounds resolveCurrentPeriodBounds() {
        return resolvePeriodBounds(LocalDate.now(DateTimeUtils.KST_ZONE));
    }

    /** 임의 날짜가 속한 한 주기의 [시작일시, 다음 주기 시작일시) 범위를 반환한다. */
    public PeriodBounds resolvePeriodBounds(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now(DateTimeUtils.KST_ZONE) : date;
        return resolveCycleBounds(resolveCycleStart(targetDate), 1);
    }

    /** 거래 시각을 한국 시간으로 변환해 해당 주기의 범위를 반환한다. */
    public PeriodBounds resolvePeriodBounds(Instant occurredAt) {
        LocalDate date = occurredAt == null
                ? LocalDate.now(DateTimeUtils.KST_ZONE)
                : occurredAt.atZone(DateTimeUtils.KST_ZONE).toLocalDate();
        return resolvePeriodBounds(date);
    }

    /** 시작 주기와 기간 수로 [시작일시, 종료일시) 범위를 반환한다. */
    public PeriodBounds resolveCycleBounds(LocalDate cycleStart, int cycleCount) {
        if (cycleStart == null || cycleCount < 1) {
            throw new IllegalArgumentException("주기 시작일과 기간 수는 필수입니다.");
        }

        Instant startAt = cycleStart
                .atStartOfDay(DateTimeUtils.KST_ZONE)
                .toInstant();
        Instant endAt = cycleStart.plusYears(cycleCount)
                .atStartOfDay(DateTimeUtils.KST_ZONE)
                .toInstant();
        return new PeriodBounds(startAt, endAt);
    }

    private LocalDate resolveCycleStart(LocalDate date) {
        int monthDay = date.getMonthValue() * 100 + date.getDayOfMonth();
        return monthDay >= 302
                ? LocalDate.of(date.getYear(), 3, 2)
                : LocalDate.of(date.getYear() - 1, 3, 2);
    }

    public record PeriodBounds(Instant startAt, Instant endAt) {
    }
}
