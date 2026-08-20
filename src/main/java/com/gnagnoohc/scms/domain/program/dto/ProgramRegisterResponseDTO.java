package com.gnagnoohc.scms.domain.program.dto;

import java.math.BigDecimal;
import java.time.Instant;

// 비교과프로그램 등록 응답 DTO. 등록 성공 후 서버가 확정한 값만 담는다.
// programId(생성된 PK), programStatus(항상 DRAFT), createdAt처럼 요청 DTO엔 없던
// 서버 결정값이 포함되고, FK id 등 클라이언트가 이미 아는 값은 응답에서 뺐다.

public record ProgramRegisterResponseDTO(
        Integer programId,
        String programName,
        String programStatus,
        Integer capacity,
        BigDecimal completionRate,
        Instant recruitmentStartsAt,
        Instant recruitmentEndsAt,
        Instant operationStartsAt,
        Instant operationEndsAt,
        Instant createdAt
) {
}
