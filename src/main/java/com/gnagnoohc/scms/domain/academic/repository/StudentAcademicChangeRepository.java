package com.gnagnoohc.scms.domain.academic.repository;

import com.gnagnoohc.scms.domain.academic.entity.StudentAcademicChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentAcademicChangeRepository extends JpaRepository<StudentAcademicChange, Integer> {

    /**
     * 학생 1명의 전체 변동이력. change_type/change_reason 공통코드를 fetch join으로
     * 함께 가져온다
     * 오래된 변동부터(오름차순) 정렬해 "No" 컬럼을 그대로 순번으로 매길 수 있게 한다.
     */
    @Query("SELECT c FROM StudentAcademicChange c " +
            "JOIN FETCH c.changeTypeCode " +
            "LEFT JOIN FETCH c.changeReasonCode " +
            "WHERE c.student.userId = :studentId " +
            "ORDER BY c.changeDate ASC, c.studentAcademicChangeId ASC")
    List<StudentAcademicChange> findAllByStudentIdWithCodes(@Param("studentId") Integer studentId);
}
