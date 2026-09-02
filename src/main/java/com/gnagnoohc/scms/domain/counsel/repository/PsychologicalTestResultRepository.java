package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 학생 본인의 결과만 조회하도록 student.userId와 testType을 WHERE 절에서 직접 제한한다.
 * 전체 결과를 읽어 서비스에서 걸러내지 않고 DB가 페이지 단위로 잘라 반환하게 한다.
 */
public interface PsychologicalTestResultRepository extends JpaRepository<PsychologicalTestResult, Integer> {

    Page<PsychologicalTestResult> findByStudentUserIdAndTestType(
            Integer studentUserId,
            String testType,
            Pageable pageable
    );
}
