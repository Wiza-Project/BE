package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * AssessmentIntroService.startAttempt()가 uq_assessment_attempt_round_student 위반을 잡은 뒤
 * 호출한다. Postgres는 제약 위반으로 statement가 실패하면 트랜잭션 전체를 aborted 상태로 만들어서
 * 같은 트랜잭션 안에서 재조회 쿼리를 또 날려도 그대로 실패한다(AssessmentResponseService.saveResponse와
 * 동일한 제약) — 그래서 조회만 별도 트랜잭션(REQUIRES_NEW)으로 분리한다(DormantAccountLocker와 같은 이유).
 */
@Component
@RequiredArgsConstructor
public class AssessmentAttemptStartRecovery {

    private final AssessmentAttemptRepository assessmentAttemptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<AssessmentAttempt> findExisting(Integer roundId, Integer studentId) {
        return assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(roundId, studentId);
    }
}
