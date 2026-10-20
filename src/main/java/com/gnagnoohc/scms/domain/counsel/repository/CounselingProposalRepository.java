package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingProposal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 체크리스트 14 "스트레스 결과 기반 상담 제안"의 영속성 접근만 담당한다.
 * 상담사 전체 제안 이력·검색·필터 API는 현재 범위가 아니므로 여기에 메서드를 추가하지 않는다.
 */
public interface CounselingProposalRepository extends JpaRepository<CounselingProposal, Integer> {

    /**
     * 같은 결과에 대한 중복 제안을 생성 전에 먼저 걸러내는 애플리케이션 사전 검사용이다.
     * 최종 방어선은 DB의 uq_counseling_proposal_test_result 유니크 제약이며, 이 조회는
     * 그 제약이 걸리기 전에 흔한 중복 요청을 더 저렴하게 막기 위한 것일 뿐이다.
     */
    boolean existsByPsychologicalTestResultPsychologicalTestResultId(Integer resultId);

    /**
     * 학생 본인 소유 제안만 잠근다. WHERE 절에 studentId를 함께 걸어, 없는 제안인지 다른 학생
     * 소유인지를 애플리케이션에서 따로 분기하지 않고 리포지토리 한 곳에서 걸러낸다(둘 다 S016).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from CounselingProposal p
            where p.counselingProposalId = :proposalId
              and p.student.userId = :studentId
            """)
    Optional<CounselingProposal> findByIdAndStudentIdForUpdate(
            @Param("proposalId") Integer proposalId,
            @Param("studentId") Integer studentId
    );

    /**
     * 학생 본인 제안 목록 조회다. 응답 DTO가 결과·예약 필드를 바로 읽으므로 EntityGraph로
     * 함께 가져와 목록 페이지마다 N+1 지연로딩 쿼리가 나가지 않게 한다.
     */
    @EntityGraph(attributePaths = {"psychologicalTestResult", "createdReservation"})
    Page<CounselingProposal> findAllByStudentUserIdOrderByCreatedAtDescCounselingProposalIdDesc(
            Integer studentId,
            Pageable pageable
    );

    /**
     * 기한이 지난 저장 상태 PENDING 제안을 EXPIRED로 일괄 정리한다. 매분 몇 번을 실행해도 같은
     * 조건으로 같은 값을 쓰는 멱등 연산이라 여러 서버가 동시에 돌려도 안전하다.
     * respondedAt은 학생의 실제 응답이 아니므로 건드리지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update CounselingProposal p
            set p.responseStatus = 'EXPIRED'
            where p.responseStatus = 'PENDING'
              and p.responseDeadline <= :now
            """)
    int expirePastDeadline(@Param("now") Instant now);
}
