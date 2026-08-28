package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetencyRepository extends JpaRepository<Competency, Integer> {
    long countByParentCompetencyIsNull();

    // 여러 화면·도메인이 "연결할 핵심역량을 고르는" 드롭다운에서 공통으로 쓰는 목록.
    // 비교과 프로그램 등록, 마일리지 활동 유형 등록 등에서 competency_id를 지정할 때 후보로 보여준다.
    //   - ParentCompetencyIsNull : 하위역량은 개발 범위 밖이라(문항·정책 모두 최상위 역량에만 매핑) 최상위만 후보다.
    //   - ActiveTrue             : 비활성 역량을 새로 연결하면 이후 집계가 어긋나므로 후보에서 제외한다.
    //   - OrderByDisplayOrderAsc : 화면마다 순서가 달라지지 않도록 축순서(결과 차트와 같은 기준)로 고정한다.
    List<Competency> findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc();

    // 축순서 변경 시 같은 번호를 이미 쓰고 있는 다른 최상위 역량(스왑 대상)을 찾는다.
    Optional<Competency> findByParentCompetencyIsNullAndDisplayOrderAndCompetencyIdNot(Integer displayOrder, Integer competencyId);

    // 문항 엑셀 업로드 시 '상위역량' 컬럼(역량명)으로 소속 핵심역량을 매핑한다.
    Optional<Competency> findByCompetencyNameAndParentCompetencyIsNull(String competencyName);
}
