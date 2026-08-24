package com.gnagnoohc.scms.domain.academic.repository;

import com.gnagnoohc.scms.domain.academic.entity.StudentAcademicDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentAcademicDetailRepository extends JpaRepository<StudentAcademicDetail, Integer> {

    /**
     * 상세 조회 전용 — 소속학과/지도교수 공통코드·엔티티를 fetch join으로 함께 가져와
     * N+1을 막는다. 행 자체가 없을 수 있으므로(전 컬럼 nullable, 미입력 학생) 항상
     * {@link Optional}로 감싼다 — 없다고 404가 아니다.
     */
    @Query("SELECT d FROM StudentAcademicDetail d " +
            "LEFT JOIN FETCH d.majorCode " +
            "LEFT JOIN FETCH d.advisorUser " +
            "WHERE d.userId = :userId")
    Optional<StudentAcademicDetail> findWithDetailsByUserId(@Param("userId") Integer userId);
}
