package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.StudentAssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.repository.StudentAssessmentRoundQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// 학생 진단 안내 화면의 "응시 가능한 회차" 목록. 프론트가 회차 ID를 하드코딩하지 않도록,
// 응시기간 안이면서 대상 조건에 맞는 회차만 골라 내려준다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentAssessmentRoundService {

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final StudentAssessmentRoundQueryRepository studentAssessmentRoundQueryRepository;

    public List<StudentAssessmentRoundResponse> getOpenRounds(Integer studentId) {
        Instant now = Instant.now();
        return assessmentRoundRepository
                .findByStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByStartsAtDesc(now, now)
                .stream()
                .filter(round -> studentAssessmentRoundQueryRepository
                        .isStudentTargeted(studentId, round.getTargetCondition()))
                .map(round -> toResponse(round, studentId))
                .toList();
    }

    private StudentAssessmentRoundResponse toResponse(AssessmentRound round, Integer studentId) {
        long questionCount = assessmentRoundQuestionRepository
                .countByAssessmentRound_AssessmentRoundId(round.getAssessmentRoundId());
        AssessmentAttempt existing = assessmentAttemptRepository
                .findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(round.getAssessmentRoundId(), studentId)
                .orElse(null);

        return new StudentAssessmentRoundResponse(
                round.getAssessmentRoundId(),
                round.getAssessmentName(),
                round.getAssessmentType(),
                round.getStartsAt(),
                round.getEndsAt(),
                questionCount,
                existing == null ? null : existing.getAttemptId(),
                existing == null ? null : existing.getAttemptStatus()
        );
    }
}
