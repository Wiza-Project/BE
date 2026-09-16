package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 학생이 상담 제안을 수락할 때 선택한 일정과 동의를 담는다.
 * 제안한 상담사의 일정으로 제한하지 않으므로 counselorId는 받지 않는다.
 */
public record CounselingProposalAcceptRequest(
        @NotNull @Positive Integer scheduleId,
        @NotNull @Positive Integer consentId
) {
}
