package com.gnagnoohc.scms.domain.counsel.dto;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingPublicResult;

import java.time.Instant;

/**
 * 상담사 전용 공개 결과 버전 이력 한 행. 학생에게는 절대 내려주지 않는 correctionReason·createdByName을
 * 포함한다(설계 문서 5.2). 목록은 versionNo DESC로 정렬해 반환한다.
 */
public record CounselingPublicResultHistoryResponse(
        Integer publicResultId,
        Integer versionNo,
        String resultSummary,
        String actionPlan,
        String correctionReason,
        String createdByName,
        Instant publishedAt
) {
    public static CounselingPublicResultHistoryResponse from(CounselingPublicResult result, String createdByName) {
        return new CounselingPublicResultHistoryResponse(
                result.getPublicResultId(),
                result.getVersionNo(),
                result.getResultSummary(),
                result.getActionPlan(),
                result.getCorrectionReason(),
                createdByName,
                result.getPublishedAt()
        );
    }
}
