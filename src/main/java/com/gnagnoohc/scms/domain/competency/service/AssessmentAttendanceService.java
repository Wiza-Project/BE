package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentAttendanceResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttendanceQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentAttendanceService {

    private static final int RATE_SCALE = 2;

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentAttendanceQueryRepository assessmentAttendanceQueryRepository;

    public AssessmentAttendanceResponse getAttendance(Integer roundId) {
        AssessmentRound round = assessmentRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));

        long targetCount = assessmentAttendanceQueryRepository.countTargetStudents(round.getTargetCondition());
        long completedCount = assessmentAttendanceQueryRepository.countCompletedAttempts(roundId, round.getTargetCondition());

        return new AssessmentAttendanceResponse(roundId, targetCount, completedCount, calculateRate(completedCount, targetCount));
    }

    // 대상자가 0명이면(예: 조건에 맞는 학생이 없는 회차) 0/0을 나누지 않고 0%로 고정한다.
    private BigDecimal calculateRate(long completedCount, long targetCount) {
        if (targetCount == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(targetCount), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
