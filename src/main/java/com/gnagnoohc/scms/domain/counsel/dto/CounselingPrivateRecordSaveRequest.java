package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 비공개 상담 기록 저장 요청. 공백/길이 검증은 여기서 하지 않는다 — 최종 경계는
 * CounselingPrivateRecord의 도메인 메서드이며 실패 시 C001로 응답한다.
 */
public record CounselingPrivateRecordSaveRequest(
        @NotNull String privateContent
) {
}
