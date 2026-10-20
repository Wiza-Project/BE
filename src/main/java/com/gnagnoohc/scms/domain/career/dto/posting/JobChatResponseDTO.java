package com.gnagnoohc.scms.domain.career.dto.posting;

import java.util.List;

/** 채팅 답변과 DB에서 실제 조회된 추천 공고 3건. */
public record JobChatResponseDTO(
        String answer,
        JobChatCriteriaDTO criteria,
        List<JobPostingSummaryResponseDTO> recommendations
) {
}
