package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 학생 취업 희망조건 데이터 접근 계층 (Repository)
 *
 * <p><strong>[설계 원칙 및 데이터 조회 최적화 기준]</strong></p>
 * <ul>
 *   <li><b>NCS 독립성 보장:</b> 벡터 기반 인공지능 매칭 풀({@code NcsStandard})과의 물리적 외래키 의존성을
 *       배제하여 RDBMS 단위의 정합성 오류 및 불필요한 결합도를 사전 차단</li>
 *   <li><b>N+1 및 무한 조인 원천 차단:</b> 단일 연관관계 객체({@code AppUser}, {@code CommonCode})만을
 *       {@code JOIN FETCH}로 일괄 조회하며, 컬렉션 조인이 배제(카테시안 곱:리스트의 모든 행끼리 곱하면서 JPA 메모리 로딩 병목현상 등 -> 이걸 방지)</li>
 * </ul>
 *
 * @author YUN
 */
public interface JobPreferenceRepository extends JpaRepository<JobPreference, Integer> {

    @Query("SELECT jp FROM JobPreference jp " +
            "JOIN FETCH jp.student s " +
            "LEFT JOIN FETCH jp.preferredRegionCode " +
            "WHERE s.userId = :studentUserId")
    Optional<JobPreference> findByStudent_UserId(@Param("studentUserId") Integer studentUserId);

    boolean existsByStudent_UserId(Integer studentUserId);
}