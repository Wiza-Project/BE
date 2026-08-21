package com.gnagnoohc.scms.domain.program.dto.response;

import java.time.Instant;

// 비교과프로그램 참여 신청 "취소" 처리 응답 DTO.
public record ProgramApplicationCancelResponseDTO(
        Integer applicationId,
        Integer programId,

        // 처리 결과 상태 코드값. 항상 "CANCELLED".
        String applicationStatus,

        // 처리 결과 상태의 한글 라벨. "취소".
        String applicationStatusLabel,

        // 취소 사유. 학생이 입력하지 않았다면 null.
        String cancellationReason,

        Instant canceledAt
) {
}
