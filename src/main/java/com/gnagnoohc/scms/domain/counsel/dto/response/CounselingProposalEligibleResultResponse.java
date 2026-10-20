package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestResult;

import java.time.Instant;

/**
 * 상담사 제안 후보 목록의 한 행이다. 내부 학생 ID, 연락처, 학과, 결과 설명, 문항별 원응답은
 * 담지 않는다. 후보 조회는 힌트일 뿐이므로 이 응답만으로 생성 권한이 보장되지 않는다.
 */
public record CounselingProposalEligibleResultResponse(
        Integer resultId,
        String universityNo,
        String studentName,
        int totalScore,
        String resultLevel,
        Instant testedAt
) {
    public static CounselingProposalEligibleResultResponse from(PsychologicalTestResult result) {
        return new CounselingProposalEligibleResultResponse(
                result.getPsychologicalTestResultId(),
                result.getStudent().getUniversityNo(),
                result.getStudent().getUserName(),
                result.getTotalScore().intValueExact(),
                result.getResultLevel(),
                result.getTestedAt()
        );
    }
}
