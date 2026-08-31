package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.CompetencySummary;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
