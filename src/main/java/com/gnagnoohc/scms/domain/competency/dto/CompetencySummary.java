package com.gnagnoohc.scms.domain.competency.dto;

import com.gnagnoohc.scms.domain.competency.entity.Competency;

// 핵심역량 "선택 목록"용 최소 필드 뷰 (id·코드·이름·표시순서).
//
// 관리 화면용 CompetencyResponse(영문명·설명·활성여부까지 포함) 대신 이 타입을 따로 둔 이유:
//   - 드롭다운은 이 네 값만 있으면 되고, 나머지 필드는 노출할 이유가 없다.
//   - 이 목록은 비교과 프로그램·마일리지 등 다른 도메인도 받아 쓴다. Competency 엔티티를 그대로 넘기면
//     지연 로딩(parentCompetency)·연관 관계가 도메인 경계를 넘어가므로, 값만 담은 record로 끊는다.
public record CompetencySummary(
        Integer competencyId,
        String competencyCode,
        String competencyName,
        Integer displayOrder
) {
    // Competency 엔티티 한 건을 목록 응답용 값으로 옮긴다.
    public static CompetencySummary from(Competency competency) {
        return new CompetencySummary(
                competency.getCompetencyId(),
                competency.getCompetencyCode(),
                competency.getCompetencyName(),
                competency.getDisplayOrder()
        );
    }
}
