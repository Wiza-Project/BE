package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 역량진단(competency) 도메인을 수정하지 않고 완료된 응시 회차를 마일리지에서 읽는다. */
public interface AssessmentAttemptMileageRepository extends JpaRepository<AssessmentAttempt, Integer> {

    /** 적립 원장 생성에 필요한 학생을 함께 조회한다. */
    @Query("""
            select a
            from AssessmentAttempt a
            join fetch a.student
            where a.attemptId = :attemptId
            """)
    Optional<AssessmentAttempt> findWithStudentByAttemptId(@Param("attemptId") Integer attemptId);
}
