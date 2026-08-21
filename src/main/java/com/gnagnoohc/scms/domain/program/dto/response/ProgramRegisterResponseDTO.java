package com.gnagnoohc.scms.domain.program.dto.response;

// BigDecimal: completionRate처럼 소수점 계산 오차 없이 정확한 값을 담아야 하는 필드에 사용.
import java.math.BigDecimal;
// Instant: 날짜+시간을 표현하는 타입. 생성 시각(createdAt) 등에 사용.
import java.time.Instant;

/**
 * 비교과프로그램 "등록" 응답 DTO. 등록이 성공적으로 끝난 뒤 클라이언트(프론트엔드)에게 돌려줄 값들만 담는다.
 * programId(새로 생성된 PK), programStatus(항상 "모집중"), createdAt처럼 요청 DTO엔 없던
 * "서버가 결정한 값"들이 포함되고, FK id처럼 클라이언트가 이미 알고 있는 값은 응답에서 뺐다.
 */
public record ProgramRegisterResponseDTO(
        /** 새로 등록된 프로그램의 PK. DB가 자동으로 채번한 값이라 등록 전에는 클라이언트가 알 수 없으므로 응답으로 알려준다. */
        Integer programId,

        /** 등록된 프로그램명. 요청으로 받은 값을 그대로 돌려준다. */
        String programName,

        /** 프로그램 상태. 등록 직후에는 항상 "모집중" 상태로 고정된다 (한글 라벨로 노출). */
        String programStatus,

        /** 등록된 정원. */
        Integer capacity,

        /** 등록된 이수 기준 출석률(%). 요청에 없었다면 서버가 채운 기본값(80)이 여기 담긴다. */
        BigDecimal completionRate,

        /** 등록된 모집 시작 시각. */
        Instant recruitmentStartsAt,

        /** 등록된 모집 종료 시각. */
        Instant recruitmentEndsAt,

        /** 등록된 운영 시작 시각. */
        Instant operationStartsAt,

        /** 등록된 운영 종료 시각. */
        Instant operationEndsAt,

        /** 이번 등록이 실제로 반영된 시각. 클라이언트가 요청한 값이 아니라 서버가 직접 만든 "현재 시각"이다. */
        Instant createdAt
) {
}
