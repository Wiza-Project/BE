package com.gnagnoohc.scms.domain.mileage.DTO;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 학생 본인의 확정 마일리지 적립 원장과 상세 정보를 위한 응답 모델. */
public final class MileageTransactionHistoryResponse {

    private MileageTransactionHistoryResponse() {
    }

    /** 적립 원장 목록 한 건에 해당한다. */
    public record ListItem(
            Integer transactionId,
            String activityName,
            String sourceType,
            String transactionType,
            BigDecimal points,
            String transactionStatus,
            Instant occurredAt
    ) {
    }

    /** 적립 원장 상세 화면에서 사용하는 거래·적립 출처 정보다. */
    public record Detail(
            Integer transactionId,
            String transactionType,
            BigDecimal points,
            String transactionStatus,
            String transactionReason,
            Instant occurredAt,
            String sourceType,
            PolicyDetail policy,
            ProgramDetail extracurricularProgram,
            ExternalActivityDetail externalActivity
    ) {
    }

    public record PolicyDetail(
            Integer policyId,
            String activityCode,
            String activityName,
            String categoryCode,
            String earningRoute,
            String semesterCode,
            BigDecimal policyPoints
    ) {
    }

    /** 비교과 프로그램 이수로 적립된 경우의 출처다. */
    public record ProgramDetail(
            Integer applicationId,
            Integer programId,
            String programName,
            String completionStatus,
            String certificateNo,
            Instant certificateIssuedAt
    ) {
    }

    /** 외부활동·자격증 취득 신청으로 적립된 경우의 출처다. */
    public record ExternalActivityDetail(
            Integer externalClaimId,
            String activityName,
            LocalDate activityDate,
            BigDecimal requestedPoints,
            String claimStatus,
            String reviewReason,
            String activityCode,
            String activityTypeName,
            String categoryCode,
            String earningRoute,
            JsonNode detailData
    ) {
    }
}
