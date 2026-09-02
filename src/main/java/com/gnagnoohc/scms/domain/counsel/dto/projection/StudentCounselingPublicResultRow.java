package com.gnagnoohc.scms.domain.counsel.dto.projection;

import java.time.Instant;

/**
 * 학생 본인 공개 결과 목록·상세의 JPQL 프로젝션 결과다. finalResult(예약 완료·마지막 출석 완료 회기)는
 * 요청자와 무관한 값이지만 여러 예약을 배치로 조회해 계산하므로 이 레코드에는 담지 않고
 * CounselingPublicResultService가 reservationId로 별도 계산해 응답(StudentCounselingPublicResultResponse)에 붙인다.
 */
public record StudentCounselingPublicResultRow(
        Integer publicResultId,
        Integer sessionId,
        Integer reservationId,
        Integer sessionNo,
        String counselingTypeName,
        String counselorName,
        Instant startsAt,
        Instant publishedAt,
        String resultSummary,
        String actionPlan
) {
}
