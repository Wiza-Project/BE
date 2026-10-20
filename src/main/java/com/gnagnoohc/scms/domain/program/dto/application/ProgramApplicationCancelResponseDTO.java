package com.gnagnoohc.scms.domain.program.dto.application;

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

        Instant canceledAt,

        // 취소 반영 직후 기준 잔여 정원(capacity - APPLIED/APPROVED 건수). 프론트가 재신청/대기 버튼을
        // 프로그램 상세 재조회 없이 바로 판단할 수 있도록 내려준다.
        Integer remainingCapacity,

        // 이 프로그램의 모집 마감 시각. 프론트가 "신청 불가"(마감 지남) 여부를 판단하는 데 쓴다.
        Instant recruitmentEndsAt
) {
}
