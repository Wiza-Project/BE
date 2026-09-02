package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingPublicResult;

import java.time.Instant;

/**
 * 상담사의 공개 결과 조회·저장·일반 공개·최종 완료가 공통으로 반환하는 응답이다. 결과 행이 없는
 * 것도(resultStatus=EMPTY) 접근 가능한 회기의 정상 상태다. resultStatus, finalResult, 세 can*
 * 값은 DB 컬럼이 아니라 매 요청마다 계산한 값이다.
 */
public record CounselorCounselingPublicResultResponse(
        Integer sessionId,
        Integer reservationId,
        Integer assignmentId,
        Integer publicResultId,
        Integer versionNo,
        String resultSummary,
        String actionPlan,
        String resultStatus,
        String createdByName,
        Instant publishedAt,
        String reservationStatus,
        boolean assignmentActive,
        boolean privateRecordConfirmed,
        boolean finalResult,
        boolean canSaveDraft,
        boolean canPublish,
        boolean canCompleteReservation,
        boolean canCorrect
) {
    public static CounselorCounselingPublicResultResponse from(
            Integer sessionId,
            Integer reservationId,
            Integer assignmentId,
            CounselingPublicResult result,
            String createdByName,
            String reservationStatus,
            boolean assignmentActive,
            boolean privateRecordConfirmed,
            boolean finalResult,
            boolean canSaveDraft,
            boolean canPublish,
            boolean canCompleteReservation,
            boolean canCorrect
    ) {
        if (result == null) {
            return new CounselorCounselingPublicResultResponse(
                    sessionId, reservationId, assignmentId,
                    null, null, null, null, "EMPTY",
                    null, null,
                    reservationStatus, assignmentActive, privateRecordConfirmed,
                    finalResult, canSaveDraft, canPublish, canCompleteReservation, false
            );
        }
        return new CounselorCounselingPublicResultResponse(
                sessionId, reservationId, assignmentId,
                result.getPublicResultId(), result.getVersionNo(),
                result.getResultSummary(), result.getActionPlan(),
                result.isPublished() ? "PUBLISHED" : "DRAFT",
                createdByName, result.getPublishedAt(),
                reservationStatus, assignmentActive, privateRecordConfirmed,
                finalResult, canSaveDraft, canPublish, canCompleteReservation, canCorrect
        );
    }
}
