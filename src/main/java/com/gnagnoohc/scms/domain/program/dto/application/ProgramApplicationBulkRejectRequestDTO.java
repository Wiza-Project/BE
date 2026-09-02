package com.gnagnoohc.scms.domain.program.dto.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// 비교과프로그램 참여 신청 "일괄 반려" 요청 DTO. 반려 사유(reason)는 선택된 모든 건에 공통으로 적용된다.
public record ProgramApplicationBulkRejectRequestDTO(
        @NotEmpty(message = "반려할 신청 건을 하나 이상 선택해야 합니다.") List<Integer> applicationIds,
        @NotBlank(message = "반려 사유는 필수입니다.") String reason
) {
}
