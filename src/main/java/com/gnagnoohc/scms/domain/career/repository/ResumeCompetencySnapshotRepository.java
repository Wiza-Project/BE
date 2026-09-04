package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.ResumeCompetencySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeCompetencySnapshotRepository extends JpaRepository<ResumeCompetencySnapshot, Integer> {

    Optional<ResumeCompetencySnapshot> findByStudent_UserId(Integer studentUserId);

    /** 동시 최초 생성 경합 회귀 테스트용 — 학생당 정확히 1행만 남았는지 확인한다. */
    long countByStudent_UserId(Integer studentUserId);
}
