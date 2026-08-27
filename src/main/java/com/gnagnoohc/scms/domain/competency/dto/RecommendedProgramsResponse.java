package com.gnagnoohc.scms.domain.competency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 추천 비교과 프로그램 조회(GET /api/students/assessment-attempts/{attemptId}/recommended-programs) 응답.
 * 취약 역량(환산점수 하위)별로 그 역량에 연계된 모집중 프로그램을 묶어 내려준다. 취약 역량 순서는
 * 방사형 차트 축 순서가 아니라 "더 취약한 역량"이 앞에 오도록 환산점수 오름차순이다.
 * 상세 점수/차트는 결과 조회 API를 재사용하므로 여기에는 담지 않는다.
 */
public record RecommendedProgramsResponse(
        Integer attemptId,
        List<WeakCompetencyGroup> weakCompetencies
) {
    /** 취약 역량이 하나도 선정되지 않은 경우에만 사용한다. */
    public static RecommendedProgramsResponse empty(Integer attemptId) {
        return new RecommendedProgramsResponse(attemptId, List.of());
    }

    public record WeakCompetencyGroup(
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            // 이 역량이 취약으로 뽑힌 근거를 FE가 함께 보여줄 수 있도록 환산점수를 동봉한다.
            BigDecimal convertedScore,
            // 이 취약 역량에 연계된 모집 중 프로그램이 없으면 빈 목록. 그룹 자체는 유지된다.
            List<RecommendedProgram> programs
    ) {}

    public record RecommendedProgram(
            Integer programId,
            String programName,
            String operatingUnitName,
            String programTypeName,
            Integer capacity,
            long applicantCount,
            int remainingCapacity,
            Instant recruitmentStartsAt,
            Instant recruitmentEndsAt,
            Instant operationStartsAt,
            Instant operationEndsAt,
            BigDecimal mileagePoints,
            // 로그인 학생 본인의 이 프로그램 신청 상태. 신청 이력이 없거나 취소해 재신청 가능하면 null.
            String myApplicationStatus,
            String myApplicationStatusLabel
    ) {}
}
