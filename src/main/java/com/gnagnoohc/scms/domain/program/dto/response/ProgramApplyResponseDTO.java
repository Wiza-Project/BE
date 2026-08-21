package com.gnagnoohc.scms.domain.program.dto.response;

import java.time.Instant;

/** 비교과프로그램 "참여 신청" 응답 DTO. 신청이 성공적으로 접수된 뒤 클라이언트(프론트엔드)에게 돌려줄 값들만 담는다. */
public record ProgramApplyResponseDTO(
        /** 새로 생성된 신청 건의 PK. DB가 자동으로 채번한 값이라 신청 전에는 클라이언트가 알 수 없으므로 응답으로 알려준다. */
        Integer applicationId,

        /** 신청한 프로그램의 PK. 클라이언트가 이미 알고 있는 값이지만, 응답만 보고도 어떤 신청인지 알 수 있도록 포함한다. */
        Integer programId,

        /** 신청 상태 코드값. "APPLIED"(정원 내) 또는 "WAITLISTED"(대기). */
        String applicationStatus,

        /** 신청 상태의 한글 라벨. "신청완료" 또는 "대기". */
        String applicationStatusLabel,

        /** 대기 신청일 때만 값이 있는 대기순번(1부터 시작). 정원 내 신청이면 null. */
        Integer waitlistOrder,

        /** 이번 신청이 실제로 접수된 시각. 클라이언트가 요청한 값이 아니라 서버가 직접 만든 "현재 시각"이다. */
        Instant appliedAt
) {
}
