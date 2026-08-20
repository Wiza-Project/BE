package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompetencyRepository extends JpaRepository<Competency, Integer> {
    long countByParentCompetencyIsNull();

    // 축순서 변경 시 같은 번호를 이미 쓰고 있는 다른 최상위 역량(스왑 대상)을 찾는다.
    Optional<Competency> findByParentCompetencyIsNullAndDisplayOrderAndCompetencyIdNot(Integer displayOrder, Integer competencyId);
}
