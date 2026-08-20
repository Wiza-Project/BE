package com.gnagnoohc.scms.domain.program.dto;

// BigDecimal: completionRate처럼 소수점 계산 오차 없이 정확한 값을 담아야 하는 필드에 사용.
import java.math.BigDecimal;
// Instant: 날짜+시간을 표현하는 타입. 수정 시각(updatedAt) 등에 사용.
import java.time.Instant;

// 비교과프로그램 "수정" 응답 DTO. 수정이 성공적으로 끝난 뒤 클라이언트(프론트엔드)에게 돌려줄 값들만 담는다.
//
// register(등록) 응답 DTO를 그대로 재사용하지 않고 별도 타입으로 만든 이유:
//   등록 응답은 마지막 필드가 "생성 시각(createdAt)"이 의미상 맞고,
//   수정 응답은 마지막 필드가 "수정 시각(updatedAt)"이 의미상 맞기 때문이다.
//   같은 DTO를 재사용하면 필드 이름은 createdAt인데 실제로는 수정 시각이 들어가는 혼란이 생긴다.
public record ProgramUpdateResponseDTO(
        // 수정된 프로그램의 PK. 프론트가 어떤 프로그램이 수정됐는지 확인할 수 있게 그대로 돌려준다.
        Integer programId,

        // 수정된 프로그램명.
        String programName,

        // 프로그램 상태. 이 API로는 상태를 바꾸지 않지만(항상 모집중 상태에서만 호출 가능),
        // 프론트가 별도로 상세 조회를 다시 하지 않고도 현재 상태를 알 수 있도록 함께 내려준다 (한글 라벨로 노출).
        String programStatus,

        // 수정된 정원.
        Integer capacity,

        // 수정된 이수 기준 출석률(%).
        BigDecimal completionRate,

        // 수정된 모집 시작 시각.
        Instant recruitmentStartsAt,

        // 수정된 모집 종료 시각.
        Instant recruitmentEndsAt,

        // 수정된 운영 시작 시각.
        Instant operationStartsAt,

        // 수정된 운영 종료 시각.
        Instant operationEndsAt,

        // 이번 수정이 실제로 반영된 시각. 클라이언트가 요청한 값이 아니라 서버가 직접 만든 "현재 시각"이다.
        Instant updatedAt
) {
}
