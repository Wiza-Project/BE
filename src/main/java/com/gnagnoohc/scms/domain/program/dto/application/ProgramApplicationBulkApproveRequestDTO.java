package com.gnagnoohc.scms.domain.program.dto.application;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 비교과프로그램 참여 신청 "일괄 승인" 요청 DTO. 스태프가 신청관리 화면에서 여러 학생을 체크박스로
 * 선택해 한 번에 승인 처리할 때 사용한다 (건별로 정원 초과 등의 이유로 실패할 수 있어, 응답은
 * 성공/실패 목록을 함께 돌려준다 — ProgramApplicationBulkDecisionResponseDTO 참고).
 */
public record ProgramApplicationBulkApproveRequestDTO(
        @NotEmpty(message = "승인할 신청 건을 하나 이상 선택해야 합니다.") List<Integer> applicationIds
) {
}
