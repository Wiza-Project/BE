package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.ResumeCompetencySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeCompetencySnapshotRepository extends JpaRepository<ResumeCompetencySnapshot, Integer> {

    Optional<ResumeCompetencySnapshot> findByStudent_UserId(Integer studentUserId);
}
