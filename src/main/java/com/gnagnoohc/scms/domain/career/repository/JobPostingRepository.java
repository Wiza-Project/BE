package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 채용공고 메인 레포지토리 인터페이스
 *
 * <p><strong>[인터페이스 통합 및 단건/벌크 연산 설계 기준]</strong></p>
 * <p>기본 JPA CRUD 기능과 QueryDSL 커스텀 동적 쿼리 인터페이스({@code JobPostingRepositoryCustom})를 단일 창구로 통합 제공</p>
 *
 * <hr>
 * <h3>1. 상세 단건 조회 최적화 ({@code findByIdWithDetails})</h3>
 * <ul>
 *   <li><b>단일 쿼리 패치 조인:</b> 공고 상세 화면에서 즉시 필요한 기업 정보({@code companyAccount}), 직무 공통코드({@code ncsCode}), 지역 공통코드({@code regionCode})를 단 한 번의 SELECT 문으로 함께 조회하여 지연 로딩으로 인한 N+1 쿼리 발생을 원천 차단 처리</li>
 *   <li><b>동적 쿼리와의 역할 분리:</b> 조건에 따른 다건 목록 검색은 QueryDSL 구현체({@code searchStudentPostings}, {@code searchStaffPostings})가 담당하고, 고정된 PK 기반 상세 단건 조작은 본 JPQL 패치 조인 메서드가 전담하여 복잡도 낮추기</li>
 * </ul>
 *
 * <hr>
 * <h3>2. 마감 스케줄러 벌크 수정 ({@code closeExpiredPostings})</h3>
 * <ul>
 *   <li><b>대량 데이터 벌크 UPDATE:</b> 마감 일시({@code applicationEndsAt})가 지난 게시 중({@code PUBLISHED}) 공고들을 단일 UPDATE 쿼리로 한 번에 {@code CLOSED} 상태로 일괄 갱신</li>
 *   <li><b>영속성 컨텍스트 동기화 (1차 캐시 초기화):</b> 벌크 연산은 1차 캐시를 거치지 않고 DB에 직접 실행되므로, 갱신 후 데이터 불일치(Dirty Read)를 방지하기 위해 {@code @Modifying(clearAutomatically = true)} 옵션을 통해 영속성 컨텍스트 즉시 초기화</li>
 * </ul>
 *
 * @author YUN
 */
public interface JobPostingRepository extends JpaRepository<JobPosting, Integer>, JobPostingRepositoryCustom {

    /**
     * 채용공고 상세 단건 조회 (Fetch Join)
     */
    @Query("SELECT jp FROM JobPosting jp " +
            "JOIN FETCH jp.companyAccount ca " +
            "LEFT JOIN FETCH jp.ncsCode nc " +
            "LEFT JOIN FETCH jp.regionCode rc " +
            "WHERE jp.jobPostingId = :jobPostingId")
    Optional<JobPosting> findByIdWithDetails(@Param("jobPostingId") Integer jobPostingId);

    /**
     * [스케줄러용] 마감 일시 지난 게시 공고 일괄 마감(CLOSED) 처리
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobPosting jp SET jp.postingStatus = 'CLOSED' " +
            "WHERE jp.postingStatus = 'PUBLISHED' AND jp.applicationEndsAt < :now")
    int closeExpiredPostings(@Param("now") Instant now);
}