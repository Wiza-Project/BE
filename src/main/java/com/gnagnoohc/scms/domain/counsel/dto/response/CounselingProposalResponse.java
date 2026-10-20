package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingProposal;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestResult;

import java.time.Instant;

/**
 * 생성·목록·수락·거절 네 API가 공통으로 쓰는 제안 응답이다. 제안자 ID·이름은 현재 화면에
 * 필요하지 않으므로 담지 않는다. respondedAt·createdReservationId는 null이면 전역
 * non-null 직렬화 정책에 따라 응답 바디에서 생략될 수 있다.
 */
public record CounselingProposalResponse(
        Integer proposalId,
        Integer resultId,
        int totalScore,
        String resultLevel,
        Instant testedAt,
        String proposalContent,
        String responseStatus,
        Instant responseDeadline,
        Instant respondedAt,
        Integer createdReservationId,
        Instant createdAt
) {
    /**
     * responseStatus는 저장값을 그대로 쓰지 않고 CounselingProposal.effectiveResponseStatus(now)로
     * 계산한다. 스케줄러가 아직 EXPIRED로 정리하지 않은 PENDING 저장값도 API에는 기한 경과 시
     * 즉시 EXPIRED로 보여야 하기 때문이다.
     */
    public static CounselingProposalResponse from(CounselingProposal proposal, Instant now) {
        PsychologicalTestResult result = proposal.getPsychologicalTestResult();
        CounselingReservation reservation = proposal.getCreatedReservation();
        return new CounselingProposalResponse(
                proposal.getCounselingProposalId(),
                result.getPsychologicalTestResultId(),
                result.getTotalScore().intValueExact(),
                result.getResultLevel(),
                result.getTestedAt(),
                proposal.getProposalContent(),
                proposal.effectiveResponseStatus(now),
                proposal.getResponseDeadline(),
                proposal.getRespondedAt(),
                reservation == null ? null : reservation.getCounselingReservationId(),
                proposal.getCreatedAt()
        );
    }
}
