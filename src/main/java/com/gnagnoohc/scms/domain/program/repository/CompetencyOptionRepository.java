package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetencyOptionRepository extends JpaRepository<Competency, Integer> {
    List<Competency> findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc();
}
