package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
 *   <li><b>N+1 및 무한 조인 원천 차단:</b> 단일 연관관계 객체(학생 {@code AppUser},희망 직무 ncs코드태그 및 지역 {@code CommonCode})만을
 *       {@code JOIN FETCH}로 일괄 조회하며, 컬렉션 조인이 배제(카테시안 곱:리스트의 모든 행끼리 곱하면서 JPA 메모리 로딩 병목현상 등 -> 이걸 방지)</li>
 * </ul>
 *
 * @author YUN
 */
public interface JobPreferenceRepository extends JpaRepository<JobPreference, Integer> {

    /**
     * 학생 계정 식별자(PK)를 기준으로 등록된 취업 희망조건 단건을 페치 조인으로 조회
     *
     * [단건 조회 및 동시성 락] 학생 PK와 채용공고 PK로 관계 엔티티 조회 (비관적 쓰기 락 적용)
     *
     * <p>동시에 toggleScrap과 applyJob이 호출되더라도 한 트랜잭션이 완료될 때까지
     * 다른 트랜잭션을 대기시켜 덮어쓰기(Lost Update)를 원천 차단</p>
     *
     * <p>단일 연관관계 객체({@code AppUser}, {@code CommonCode})만을 페치 조인 처리
     * & ncsCode와 regionCode는 CommonCode 공통코드</p>
     *
     * @param studentUserId 대상 학생 계정 식별자 (PK)
     * @return 취업 희망조건 엔티티를 포함한 {@link Optional} 객체
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT jp FROM JobPreference jp " +
            "JOIN FETCH jp.student s " +
            "LEFT JOIN FETCH jp.ncsCode " +
            "LEFT JOIN FETCH jp.regionCode " +
            "WHERE s.userId = :studentUserId")
    Optional<JobPreference> findByStudent_UserId(@Param("studentUserId") Integer studentUserId);

    /**
     * 특정 학생의 취업 희망조건 데이터 존재 여부 판별하는 UPSERT 전 방어 로직
     * <p>신규 생성 및 기존 수정 분기 처리를 위한 사전 무결성 검증용 쿼리로 활용 예정</p>
     *
     * @param studentUserId 대상 학생 계정 식별자 (PK)
     * @return 등록 이력이 존재할 경우 true, 미등록 상태일 경우 false
     */
    boolean existsByStudent_UserId(Integer studentUserId);
}