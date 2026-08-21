package com.gnagnoohc.scms.domain.program.dto.response;

import java.time.Instant;

// 비교과프로그램 참여 신청 "승인/반려" 처리 응답 DTO. 두 처리 모두 같은 형태의 응답을 돌려준다.
public record ProgramApplicationDecisionResponseDTO(
        Integer applicationId,
        Integer programId,

        // 처리 결과 상태 코드값. "APPROVED"(승인) 또는 "REJECTED"(반려).
        String applicationStatus,

        // 처리 결과 상태의 한글 라벨. "승인" 또는 "반려".
        String applicationStatusLabel,

        // 반려 사유. 승인 처리라면 null.
        String decisionReason,

        // 처리한 운영부서 담당자의 user_id.
        Integer processedBy,

        Instant processedAt
) {
}
