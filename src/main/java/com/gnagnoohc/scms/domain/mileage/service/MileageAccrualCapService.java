package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 학기·학년도·전체 누적 마일리지 적립 상한을 반영해 실제 지급 가능 점수를 계산한다. */
@Component
@RequiredArgsConstructor
public class MileageAccrualCapService {

    static final BigDecimal SEMESTER_CAP = new BigDecimal("50");
    static final BigDecimal ACADEMIC_YEAR_CAP = new BigDecimal("100");
    static final BigDecimal LIFETIME_CAP = new BigDecimal("400");
    private static final String ALL_SEMESTER_CODE = "ALL";

    private final MileageTransactionRepository mileageTransactionRepository;

    /**
     * 요청 점수를 학기(50)·학년도(100)·전체 누적(400) 상한에 맞춰 클램프한다.
     * 정책이 특정 학기에 귀속되지 않은 연간(ALL) 정책이면 학기 상한 검사는 건너뛴다.
     */
    public BigDecimal computeGrantablePoints(Integer studentId, MileagePolicy policy, BigDecimal requestedPoints) {
        if (requestedPoints == null || requestedPoints.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal remaining = requestedPoints;

        if (policy.getAcademicYear() != null && !ALL_SEMESTER_CODE.equals(policy.getSemesterCode())) {
            BigDecimal semesterUsed = mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(
                    studentId, policy.getAcademicYear(), policy.getSemesterCode());
            remaining = remaining.min(remainingOf(SEMESTER_CAP, semesterUsed));
        }
        if (policy.getAcademicYear() != null) {
            BigDecimal yearUsed = mileageTransactionRepository.sumPostedPointsByStudentAndAcademicYear(
                    studentId, policy.getAcademicYear());
            remaining = remaining.min(remainingOf(ACADEMIC_YEAR_CAP, yearUsed));
        }
        BigDecimal lifetimeUsed = mileageTransactionRepository.sumPostedPointsByStudent(studentId);
        remaining = remaining.min(remainingOf(LIFETIME_CAP, lifetimeUsed));

        return remaining.max(BigDecimal.ZERO);
    }

    private BigDecimal remainingOf(BigDecimal cap, BigDecimal used) {
        return cap.subtract(used == null ? BigDecimal.ZERO : used).max(BigDecimal.ZERO);
    }
}
