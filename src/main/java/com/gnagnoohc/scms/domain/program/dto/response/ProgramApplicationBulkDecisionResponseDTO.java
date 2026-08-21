package com.gnagnoohc.scms.domain.program.dto.response;

import java.util.List;

/**
 * 비교과프로그램 참여 신청 "일괄 승인/반려" 응답 DTO. 건별로 정원 초과·이미 처리됨 등의 이유로
 * 실패할 수 있어(예: 승인 도중 정원이 차버린 경우), 전체를 한 번에 실패시키지 않고 성공/실패를
 * 나누어 응답한다 — 실패한 건만 프론트가 다시 보여주고 재시도하게 할 수 있다.
 */
public record ProgramApplicationBulkDecisionResponseDTO(
        List<ProgramApplicationDecisionResponseDTO> succeeded,
        List<Failure> failed
) {
    /** errorCode/message는 ErrorCode를 그대로 노출한다(예: "P004", "모집 정원이 초과되었습니다."). */
    public record Failure(Integer applicationId, String errorCode, String message) {
    }
}
