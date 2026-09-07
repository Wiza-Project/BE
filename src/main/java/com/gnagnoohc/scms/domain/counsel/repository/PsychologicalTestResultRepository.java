package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.PsychologicalTestResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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

    /**
     * 상담 제안 후보(체크리스트 14) 전용 조회다. 학생별 가장 최근 STRESS 결과 중 17점 이상이면서
     * 아직 제안이 없는 결과만 반환한다. 두 not exists가 각각 "이 결과보다 더 최신인 같은 학생의
     * STRESS 결과가 없음"과 "이 결과를 참조하는 제안이 없음"을 의미하며, 애플리케이션에서 전체 결과를
     * 읽어 학생별로 필터링하지 않고 DB가 조건을 직접 판정하게 한다. 정렬은 상담사 화면이 기대하는
     * testedAt DESC, resultId DESC로 쿼리 자체에 고정하므로 Pageable의 Sort는 사용하지 않는다.
     * EntityGraph로 student를 함께 읽어 응답 DTO 변환(universityNo·userName)에서 N+1이 나지 않게 한다.
     */
    @EntityGraph(attributePaths = "student")
    @Query(value = """
            select r
            from PsychologicalTestResult r
            join r.student s
            where r.testType = 'STRESS'
              and r.totalScore >= 17
              and s.userType = 'STUDENT'
              and s.accountStatus = 'ACTIVE'
              and not exists (
                  select 1
                  from PsychologicalTestResult newer
                  where newer.student.userId = r.student.userId
                    and newer.testType = 'STRESS'
                    and (newer.testedAt > r.testedAt
                         or (newer.testedAt = r.testedAt
                             and newer.psychologicalTestResultId > r.psychologicalTestResultId))
              )
              and not exists (
                  select 1
                  from CounselingProposal p
                  where p.psychologicalTestResult = r
              )
            order by r.testedAt desc, r.psychologicalTestResultId desc
            """,
           countQuery = """
            select count(r)
            from PsychologicalTestResult r
            join r.student s
            where r.testType = 'STRESS'
              and r.totalScore >= 17
              and s.userType = 'STUDENT'
              and s.accountStatus = 'ACTIVE'
              and not exists (
                  select 1
                  from PsychologicalTestResult newer
                  where newer.student.userId = r.student.userId
                    and newer.testType = 'STRESS'
                    and (newer.testedAt > r.testedAt
                         or (newer.testedAt = r.testedAt
                             and newer.psychologicalTestResultId > r.psychologicalTestResultId))
              )
              and not exists (
                  select 1
                  from CounselingProposal p
                  where p.psychologicalTestResult = r
              )
            """)
    Page<PsychologicalTestResult> findEligibleResults(Pageable pageable);

    /**
     * 상담 제안 생성 트랜잭션이 선택 결과를 다시 확인할 때 쓰는 잠금 조회다.
     * 후보 조회(findEligibleResults)는 잠그지 않지만, 실제 생성 시점에는 이 결과가 여전히
     * 최신·고득점·미제안인지 재검증해야 하므로 PESSIMISTIC_WRITE로 행을 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PsychologicalTestResult r where r.psychologicalTestResultId = :resultId")
    Optional<PsychologicalTestResult> findByIdForUpdate(@Param("resultId") Integer resultId);

    /**
     * 학생 행 잠금 아래에서 "지금 이 학생의 최신 STRESS 결과가 무엇인지"를 다시 확인하는 데 쓴다.
     * 선택 결과와 이 조회 결과의 ID가 같아야만 "여전히 최신"이라고 판정할 수 있다(설계 5.1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PsychologicalTestResult> findFirstByStudentUserIdAndTestTypeOrderByTestedAtDescPsychologicalTestResultIdDesc(
            Integer studentUserId,
            String testType
    );
}
