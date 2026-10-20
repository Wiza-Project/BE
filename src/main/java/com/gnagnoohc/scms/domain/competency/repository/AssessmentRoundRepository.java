package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssessmentRoundRepository extends JpaRepository<AssessmentRound, Integer> {
    // 교직원 회차 관리 화면 목록 — 최근 개설 순.
    List<AssessmentRound> findAllByOrderByStartsAtDesc();

    // 학생 진단 안내 — 지금이 응시기간 안(startsAt <= now <= endsAt)인 회차.
    List<AssessmentRound> findByStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByStartsAtDesc(
            Instant startBound, Instant endBound);

    Optional<AssessmentRound> findByAcademicYearAndSemesterCodeAndAssessmentType(
            Integer academicYear, String semesterCode, String assessmentType);

    Optional<AssessmentRound> findByAcademicYearAndSemesterCodeAndAssessmentTypeAndAssessmentRoundIdNot(
            Integer academicYear, String semesterCode, String assessmentType, Integer assessmentRoundId);

    // 백분위 산출 배치(AssessmentPercentileBatchService) 대상 조회: 응시기간이 끝났지만 아직 완료 표시가 안 된 회차.
    List<AssessmentRound> findByEndsAtBeforeAndRoundStatusNot(Instant now, String roundStatus);

    // 재학생 한정 백분위 재계산 백필(AssessmentPercentileBackfillRunner) 대상 조회: 이미 완료 처리된 회차 전체.
    List<AssessmentRound> findByRoundStatus(String roundStatus);

    /**
     * AssessmentSubmissionService(제출)와 AssessmentPercentileBatchService(백분위 완료 처리)가 같은 회차
     * 행에 걸어 서로를 직렬화하는 잠금. 둘 중 먼저 이 잠금을 잡은 트랜잭션이 커밋될 때까지 나머지는 대기했다가
     * 최신 상태(round_status, ends_at)를 기준으로 재검증한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AssessmentRound r WHERE r.assessmentRoundId = :roundId")
    Optional<AssessmentRound> findByIdForUpdate(@Param("roundId") Integer roundId);
}
