package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.dto.projection.StudentCounselingPublicResultRow;

import java.time.Instant;

/**
 * 학생 본인 공개 결과 목록·상세 응답이다. 비공개 기록, 내부 작성자 ID, followUpData,
 * correctionReason, nextSessionAt은 포함하지 않는다(consultation-domain-api.md 공개 상담 결과 절).
 */
public record StudentCounselingPublicResultResponse(
        Integer publicResultId,
        Integer sessionId,
        Integer reservationId,
        Integer sessionNo,
        String counselingTypeName,
        String counselorName,
        Instant startsAt,
        Instant publishedAt,
        String resultSummary,
        String actionPlan,
        boolean finalResult
) {
    public static StudentCounselingPublicResultResponse from(StudentCounselingPublicResultRow row, boolean finalResult) {
        return new StudentCounselingPublicResultResponse(
                row.publicResultId(),
                row.sessionId(),
                row.reservationId(),
                row.sessionNo(),
                row.counselingTypeName(),
                row.counselorName(),
                row.startsAt(),
                row.publishedAt(),
                row.resultSummary(),
                row.actionPlan(),
                finalResult
        );
    }
}
