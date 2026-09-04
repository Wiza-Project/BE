package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 학기·학년도·전체 누적 마일리지 적립 상한을 반영해 실제 지급 가능 점수를 계산한다. */
@Component
public class MileageAccrualCapService {

    static final BigDecimal SEMESTER_CAP = new BigDecimal("50");
    static final BigDecimal ACADEMIC_YEAR_CAP = new BigDecimal("100");
    static final BigDecimal LIFETIME_CAP = new BigDecimal("400");
    private static final String ALL_SEMESTER_CODE = "ALL";

    private final MileageTransactionRepository mileageTransactionRepository;
    private final MileageAcademicPeriodService mileageAcademicPeriodService;
    private final AppUserRepository appUserRepository;

    @Autowired
    public MileageAccrualCapService(
            MileageTransactionRepository mileageTransactionRepository,
            MileageAcademicPeriodService mileageAcademicPeriodService,
            AppUserRepository appUserRepository
    ) {
        this.mileageTransactionRepository = mileageTransactionRepository;
        this.mileageAcademicPeriodService = mileageAcademicPeriodService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * 요청 점수를 학기(50)·학년도(100)·전체 누적(400) 상한에 맞춰 클램프한다.
     * 정책이 특정 학기에 귀속되지 않은 연간(ALL) 정책이면 학기 상한 검사는 건너뛴다.
     */
    public BigDecimal computeGrantablePoints(
            Integer studentId,
            MileagePolicy policy,
            BigDecimal requestedPoints
    ) {
        return computeGrantablePoints(
                studentId,
                policy,
                requestedPoints,
                LocalDate.now(DateTimeUtils.KST_ZONE));
    }

    /** 적립 기준일이 속한 학사기간을 기준으로 학기·학년도 상한을 계산한다. */
    public BigDecimal computeGrantablePoints(
            Integer studentId,
            MileagePolicy policy,
            BigDecimal requestedPoints,
            LocalDate accrualDate
    ) {
        if (requestedPoints == null || requestedPoints.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        appUserRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        BigDecimal remaining = requestedPoints;
        MileageAcademicPeriodService.AcademicYearBounds academicYearBounds =
                mileageAcademicPeriodService.resolveAcademicYearBounds(
                        mileageAcademicPeriodService.resolveAcademicYear(accrualDate));

        if (!ALL_SEMESTER_CODE.equalsIgnoreCase(policy.getSemesterCode())) {
            BigDecimal semesterUsed = mileageTransactionRepository.sumPostedPointsByStudentAndExactSemester(
                    studentId,
                    academicYearBounds.startAt(),
                    academicYearBounds.endAt(),
                    policy.getSemesterCode());
            remaining = remaining.min(remainingOf(SEMESTER_CAP, semesterUsed));
        }
        BigDecimal yearUsed = mileageTransactionRepository.sumPostedPointsByStudentBetween(
                studentId, academicYearBounds.startAt(), academicYearBounds.endAt());
        remaining = remaining.min(remainingOf(ACADEMIC_YEAR_CAP, yearUsed));
        BigDecimal lifetimeUsed = mileageTransactionRepository.sumPostedPointsByStudent(studentId);
        remaining = remaining.min(remainingOf(LIFETIME_CAP, lifetimeUsed));

        return remaining.max(BigDecimal.ZERO);
    }

    private BigDecimal remainingOf(BigDecimal cap, BigDecimal used) {
        return cap.subtract(used == null ? BigDecimal.ZERO : used).max(BigDecimal.ZERO);
    }
}
