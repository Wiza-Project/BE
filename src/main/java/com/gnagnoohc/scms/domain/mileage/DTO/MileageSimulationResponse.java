package com.gnagnoohc.scms.domain.mileage.DTO;

import java.math.BigDecimal;
import java.util.List;

/** 학생 마일리지 시뮬레이션 화면에 필요한 조회·계산 결과다. */
public final class MileageSimulationResponse {

    private MileageSimulationResponse() {
    }

    /** 목표 선택과 활동 카드 조회에 사용하는 선택지다. */
    public record Options(
            Period period,
            BigDecimal currentPoints,
            List<TargetOption> targets,
            List<ActivityOption> activities
    ) {
    }

    /** 시뮬레이션 조회 기간이다. */
    public record Period(
            Integer academicYear,
            String semesterCode
    ) {
    }

    /** 인증·장학 정책별 현재 점수와 목표 달성 진행도다. */
    public record TargetOption(
            Integer benefitPolicyId,
            String benefitType,
            String benefitName,
            String semesterCode,
            BigDecimal targetPoints,
            BigDecimal currentPoints,
            BigDecimal shortagePoints
    ) {
    }

    /** 시뮬레이션에 추가할 수 있는 활동 정책이다. */
    public record ActivityOption(
            Integer mileagePolicyId,
            String activityCode,
            String activityName,
            String categoryCode,
            String earningRoute,
            BigDecimal points,
            BigDecimal maximumPoints
    ) {
    }

    /** 선택한 예정 활동을 반영한 계산 결과다. */
    public record Result(
            Period period,
            Target target,
            BigDecimal currentPoints,
            BigDecimal plannedPoints,
            BigDecimal projectedPoints,
            BigDecimal shortagePoints,
            boolean achieved,
            List<PlannedActivity> plannedActivities
    ) {
    }

    /** 계산에 사용한 목표 정보다. */
    public record Target(
            Integer benefitPolicyId,
            String benefitType,
            String benefitName,
            BigDecimal targetPoints
    ) {
    }

    /** 활동별 예정 횟수와 반영 점수다. */
    public record PlannedActivity(
            Integer mileagePolicyId,
            String activityCode,
            String activityName,
            BigDecimal unitPoints,
            Integer quantity,
            BigDecimal plannedPoints
    ) {
    }
}
