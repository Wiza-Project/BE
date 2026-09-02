package com.gnagnoohc.scms.domain.competency.dto.response;

import com.gnagnoohc.scms.domain.competency.entity.Competency;

/**
 * 핵심역량 "선택 목록"용 최소 필드 뷰 (id·코드·이름·표시순서·활성여부).
 *
 * <p>관리 화면용 CompetencyResponse(영문명·설명까지 포함) 대신 이 타입을 따로 둔 이유:
 * <ul>
 *   <li>드롭다운은 이 몇 값만 있으면 되고, 나머지 필드는 노출할 이유가 없다.</li>
 *   <li>이 목록은 비교과 프로그램·마일리지 등 다른 도메인도 받아 쓴다. Competency 엔티티를 그대로 넘기면
 *       지연 로딩(parentCompetency)·연관 관계가 도메인 경계를 넘어가므로, 값만 담은 record로 끊는다.</li>
 * </ul>
 *
 * <p>active는 프로그램 수정 화면처럼 "이미 연결된 비활성 역량"을 예외로 끼워 받는 호출부가
 * 그 항목을 회색 처리 등으로 구분하기 위한 값이다. 기본 목록은 활성만 담기므로 늘 true다.
 */
public record CompetencySummary(
        Integer competencyId,
        String competencyCode,
        String competencyName,
        Integer displayOrder,
        boolean active
) {
    // Competency 엔티티 한 건을 목록 응답용 값으로 옮긴다.
    public static CompetencySummary from(Competency competency) {
        return new CompetencySummary(
                competency.getCompetencyId(),
                competency.getCompetencyCode(),
                competency.getCompetencyName(),
                competency.getDisplayOrder(),
                competency.isActive()
        );
    }
}
