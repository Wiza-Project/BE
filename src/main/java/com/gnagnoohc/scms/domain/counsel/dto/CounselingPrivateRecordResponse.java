package com.gnagnoohc.scms.domain.counsel.dto;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingPrivateRecord;

import java.time.Instant;

/**
 * 비공개 상담 기록 응답. confirmedBy는 노출하지 않는다(원문과 함께 조회자 이외 인물의 식별 정보를
 * 응답에 얹지 않기 위함). recordStatus는 EMPTY/DRAFT/CONFIRMED 셋 중 하나다.
 */
public record CounselingPrivateRecordResponse(
        Integer sessionId,
        Integer privateRecordId,
        Integer versionNo,
        String privateContent,
        String recordStatus,
        Instant confirmedAt,
        boolean canSaveDraft,
        boolean canConfirm
) {
    public static CounselingPrivateRecordResponse from(
            Integer sessionId, CounselingPrivateRecord record, boolean canSaveDraft, boolean canConfirm
    ) {
        if (record == null) {
            return new CounselingPrivateRecordResponse(
                    sessionId, null, null, null, "EMPTY", null, canSaveDraft, canConfirm
            );
        }
        return new CounselingPrivateRecordResponse(
                sessionId,
                record.getPrivateRecordId(),
                record.getVersionNo(),
                record.getPrivateContent(),
                record.isConfirmed() ? "CONFIRMED" : "DRAFT",
                record.getConfirmedAt(),
                canSaveDraft,
                canConfirm
        );
    }
}
