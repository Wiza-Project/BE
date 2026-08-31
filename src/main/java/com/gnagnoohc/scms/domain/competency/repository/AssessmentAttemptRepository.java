package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Integer> {
    // 실제로 문항 응답을 시작한 attempt가 있는지 판정 (동의만 하고 아직 안 푼 attempt는 "시작 안 함"으로 봄).
    boolean existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(Integer assessmentRoundId);

    // 동의 API 멱등 처리용 — 이미 동의(=attempt 생성)한 학생이 다시 요청하면 새로 만들지 않고 기존 attempt를 그대로 돌려준다.
    Optional<AssessmentAttempt> findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(
            Integer assessmentRoundId, Integer studentId);

    // 학생 응시 가능 회차 목록 — 여러 회차의 내 attempt를 한 번에 조회한다(회차별 조회 N+1 방지).
    List<AssessmentAttempt> findByAssessmentRound_AssessmentRoundIdInAndStudent_UserId(
            Collection<Integer> assessmentRoundIds, Integer studentId);

    // 이력서 재연동 요청 처리 전용 — 학생의 완료 진단(submittedAt IS NOT NULL) 중 가장 최근 1건.
    // 동시각 제출이 겹칠 때 순서가 흔들리지 않도록 attemptId를 보조 정렬키로 둔다. API에는 노출하지 않는다.
    Optional<AssessmentAttempt> findFirstByStudent_UserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescAttemptIdDesc(
            Integer studentId);

    // 초기 백필 전용 — submittedAt이 채워진 기존 attempt를 페이징 순회하며 결과 준비 이벤트를 재발행한다.
    // 엔티티 대신 id만 읽어 슬라이스로 넘기고, 실제 회차/점수 로드는 발행 시점에 건별로 한다.
    @Query("SELECT a.attemptId FROM AssessmentAttempt a WHERE a.submittedAt IS NOT NULL ORDER BY a.attemptId ASC")
    Slice<Integer> findSubmittedAttemptIds(Pageable pageable);

    // 이력서 연동 이벤트 조립에 회차 메타(진단명·학년도·학기·구분)가 필요해 assessment_round를 함께 로드한다.
    // 백필이 attempt마다 호출하므로 findById + 지연 로딩이면 회차 초기화 SELECT가 건별로 추가된다.
    @EntityGraph(attributePaths = "assessmentRound")
    Optional<AssessmentAttempt> findWithRoundByAttemptId(Integer attemptId);
}
