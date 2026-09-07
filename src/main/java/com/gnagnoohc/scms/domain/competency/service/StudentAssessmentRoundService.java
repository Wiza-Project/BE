package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.StudentAssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.repository.StudentAssessmentRoundQueryRepository;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter.StudentTargetSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 학생 진단 안내 화면의 "응시 가능한 회차" 목록. 프론트가 회차 ID를 하드코딩하지 않도록,
 * 응시기간 안이면서 대상 조건에 맞는 회차만 골라 내려준다.
 *
 * <p>회차 수(N)와 무관하게 쿼리는 고정 4개다: 열린 회차 목록 + 학적 스냅샷 1회 + 문항 수 batch + 내 attempt batch.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentAssessmentRoundService {

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final StudentAssessmentRoundQueryRepository studentAssessmentRoundQueryRepository;
    private final TargetConditionInterpreter targetConditionInterpreter;

    public List<StudentAssessmentRoundResponse> getOpenRounds(Integer studentId) {
        Instant now = Instant.now();
        List<AssessmentRound> openRounds = assessmentRoundRepository
                .findByStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByStartsAtDesc(now, now);
        if (openRounds.isEmpty()) {
            return List.of();
        }

        // 대상 조건 판정에 필요한 학적(재학 여부·학년·전공)을 1회만 조회하고, 회차별 판정은 메모리에서 한다.
        StudentTargetSnapshot snapshot = studentAssessmentRoundQueryRepository.loadTargetSnapshot(studentId);

        // attempt batch는 비재학생 분기에서 "진행 중 회차"를 가려내는 데도 쓰이므로 회차 필터보다 먼저 조회한다.
        List<Integer> openRoundIds = openRounds.stream()
                .map(AssessmentRound::getAssessmentRoundId)
                .toList();
        Map<Integer, AssessmentAttempt> attemptByRound = assessmentAttemptRepository
                .findByAssessmentRound_AssessmentRoundIdInAndStudent_UserId(openRoundIds, studentId)
                .stream()
                .collect(Collectors.toMap(
                        attempt -> attempt.getAssessmentRound().getAssessmentRoundId(),
                        Function.identity()));

        List<AssessmentRound> visibleRounds = selectVisibleRounds(openRounds, snapshot, attemptByRound);
        if (visibleRounds.isEmpty()) {
            return List.of();
        }

        List<Integer> roundIds = visibleRounds.stream()
                .map(AssessmentRound::getAssessmentRoundId)
                .toList();
        Map<Integer, Long> questionCountByRound = new HashMap<>();
        for (Object[] row : assessmentRoundQuestionRepository.countGroupedByAssessmentRoundIds(roundIds)) {
            questionCountByRound.put((Integer) row[0], (Long) row[1]);
        }

        return visibleRounds.stream()
                .map(round -> toResponse(round, questionCountByRound, attemptByRound))
                .toList();
    }

    /**
     * 재학생은 대상 조건(target_condition)에 맞는 열린 회차 전부를 본다.
     *
     * <p>비재학생은 새 응시를 못 하므로 목록이 원칙적으로 비지만, 재학 중 시작해 아직 제출하지 않은
     * attempt가 있는 회차는 남긴다 — 이 목록이 유일한 이어하기 진입점이라 지우면 진행 중이던 응답을
     * 확인할 길이 막힌다(저장·제출은 AssessmentAttemptAccessGuard가 따로 막아 새 응시로 이어지지 않는다).
     * target_condition이 없는 회차는 matches()가 항상 true라 비재학생 분기를 여기서 명시적으로 끊는다.
     */
    private List<AssessmentRound> selectVisibleRounds(
            List<AssessmentRound> openRounds,
            StudentTargetSnapshot snapshot,
            Map<Integer, AssessmentAttempt> attemptByRound) {
        if (snapshot.enrolled()) {
            return openRounds.stream()
                    .filter(round -> targetConditionInterpreter.matches(round.getTargetCondition(), snapshot))
                    .toList();
        }
        return openRounds.stream()
                .filter(round -> {
                    AssessmentAttempt attempt = attemptByRound.get(round.getAssessmentRoundId());
                    return attempt != null && attempt.getSubmittedAt() == null;
                })
                .toList();
    }

    private StudentAssessmentRoundResponse toResponse(
            AssessmentRound round,
            Map<Integer, Long> questionCountByRound,
            Map<Integer, AssessmentAttempt> attemptByRound) {
        Integer roundId = round.getAssessmentRoundId();
        AssessmentAttempt existing = attemptByRound.get(roundId);
        return new StudentAssessmentRoundResponse(
                roundId,
                round.getAssessmentName(),
                round.getAssessmentType(),
                round.getStartsAt(),
                round.getEndsAt(),
                questionCountByRound.getOrDefault(roundId, 0L),
                existing == null ? null : existing.getAttemptId(),
                existing == null ? null : existing.getAttemptStatus()
        );
    }
}
