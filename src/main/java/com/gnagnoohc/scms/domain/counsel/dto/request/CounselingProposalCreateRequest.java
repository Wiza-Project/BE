package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 상담사(ST200 only)가 제안을 생성할 때 보내는 요청이다.
 * studentId·proposedBy·responseDeadline·점수·결과 수준·상태는 받지 않는다.
 * 학생과 점수는 결과에서, 제안자는 인증 주체에서, 상태·기한은 서버가 직접 정한다.
 */
public record CounselingProposalCreateRequest(
        @NotNull @Positive Integer psychologicalTestResultId,
        @NotBlank @Size(max = 1000) String proposalContent
) {
    public CounselingProposalCreateRequest {
        proposalContent = proposalContent == null ? null : proposalContent.trim();
    }
}
