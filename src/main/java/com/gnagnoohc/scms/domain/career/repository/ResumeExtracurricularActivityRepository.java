package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.ResumeExtracurricularActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResumeExtracurricularActivityRepository extends JpaRepository<ResumeExtracurricularActivity, Integer> {

    /** 이벤트 upsert 전 멱등 확인용 — application_id UNIQUE 제약과 짝을 이룬다. */
    boolean existsByApplicationId(Integer applicationId);

    /**
     * 학생 본인의 수료 이력을 이수일(운영 종료일) 기준 최신순으로 조회한다. 같은 시각에 종료된 프로그램이
     * 여럿이면 id 역순(최근 적재분 우선)으로 안정적인 순서를 보장한다.
     */
    @Query("""
            SELECT a FROM ResumeExtracurricularActivity a
            WHERE a.student.userId = :studentId
            ORDER BY a.operationEndedAt DESC, a.resumeExtracurricularActivityId DESC
            """)
    List<ResumeExtracurricularActivity> findAllByStudentIdOrderByOperationEndedAtDesc(@Param("studentId") Integer studentId);
}
