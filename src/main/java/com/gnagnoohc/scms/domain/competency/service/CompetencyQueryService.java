package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.CompetencySummary;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 핵심역량 목록을 "읽기만" 하는 진입점.
//
// CompetencyService(등록·축순서·사용여부 변경)와 분리한 이유:
//   - 성격이 다르다. 이쪽은 조회 전용이라 클래스 전체를 readOnly 트랜잭션으로 묶는다.
//   - 비교과 프로그램·마일리지 등 다른 도메인이 "핵심역량 후보 목록"을 필요로 할 때, 각자
//     CompetencyRepository를 직접 주입하거나 Competency 엔티티를 다루지 않고 이 서비스만 호출하게 한다.
//     후보를 고르는 규칙(활성·최상위·정렬)이 여기 한 곳에 모여 있어, 하위역량 도입 등으로 규칙이
//     바뀌어도 이 클래스만 고치면 된다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetencyQueryService {

    private final CompetencyRepository competencyRepository;

    // 활성 상태인 최상위 핵심역량을 축순서대로 반환한다. 없으면 빈 리스트(널 아님).
    // 프로그램 필터(학생)·프로그램 등록 폼·마일리지 활동 유형 등록 폼에서 역량을 고르는 셀렉트 박스의 원본 데이터다.
    public List<CompetencySummary> getActiveTopLevelCompetencies() {
        return competencyRepository.findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CompetencySummary::from)
                .toList();
    }

    // 프로그램 수정 화면 전용: 기본 목록(활성 최상위)은 그대로 두고, 그 프로그램이 이미 참조 중인 역량 한 건만
    // 예외로 포함해 준다. 등록 후 관리자가 그 역량을 비활성화했거나 목록 밖(하위역량 등)인 경우에도 수정 폼
    // 드롭다운에 현재 값이 떠야 하기 때문이다.
    //   - includeCompetencyId가 null이면 기본 목록과 동일하다.
    //   - 이미 활성 목록에 있으면 중복해서 넣지 않는다.
    //   - 존재하지 않는 id면(참조가 꼬인 경우) 무시하고 활성 목록만 반환한다 — 수정 폼이 깨지지 않도록.
    //   - 예외로 끼워 넣는 항목은 축순서 정렬된 활성 목록 "뒤"에 붙는다(active 값으로 프론트가 구분 표시).
    public List<CompetencySummary> getActiveTopLevelCompetencies(Integer includeCompetencyId) {
        List<CompetencySummary> active = getActiveTopLevelCompetencies();
        if (includeCompetencyId == null) {
            return active;
        }
        boolean alreadyIncluded = active.stream()
                .anyMatch(competency -> includeCompetencyId.equals(competency.competencyId()));
        if (alreadyIncluded) {
            return active;
        }
        return competencyRepository.findById(includeCompetencyId)
                .map(extra -> {
                    List<CompetencySummary> merged = new ArrayList<>(active);
                    merged.add(CompetencySummary.from(extra));
                    return List.copyOf(merged);
                })
                .orElse(active);
    }
}
