package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageGradeResponse;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MileageGradeServiceTest {

    private static final Integer STUDENT_ID = 7;
    private static final Integer ACADEMIC_YEAR = 2026;
    private static final String SEMESTER_CODE = "FALL";

    @Mock
    private MileageTransactionRepository mileageTransactionRepository;

    @Mock
    private MileageBenefitPolicyRepository mileageBenefitPolicyRepository;

    private MileageGradeService mileageGradeService;

    @BeforeEach
    void setUp() {
        mileageGradeService = new MileageGradeService(
                mileageTransactionRepository,
                mileageBenefitPolicyRepository);
    }

    @Test
    void getGrade_returnsCurrentAndNextGradeFromCumulativePostedPoints() {
        when(mileageTransactionRepository.sumPostedPointsByStudent(STUDENT_ID))
                .thenReturn(new BigDecimal("350"));
        List<MileageBenefitPolicy> policies = List.of(
                policy(1, "브론즈", "ALL", "0"),
                policy(2, "실버", "FALL", "300"),
                policy(3, "골드", "ALL", "500")
        );
        when(mileageBenefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        "GRADE", ACADEMIC_YEAR, List.of(SEMESTER_CODE, "ALL")))
                .thenReturn(policies);

        MileageGradeResponse response = mileageGradeService.getGrade(
                STUDENT_ID, ACADEMIC_YEAR, SEMESTER_CODE);

        assertThat(response.cumulativePoints()).isEqualByComparingTo("350");
        assertThat(response.currentGrade().gradeName()).isEqualTo("실버");
        assertThat(response.currentGrade().semesterCode()).isEqualTo("FALL");
        assertThat(response.nextGrade().gradeName()).isEqualTo("골드");
        assertThat(response.pointsToNextGrade()).isEqualByComparingTo("150");
    }

    @Test
    void getGrade_includesGradeAtExactMinimumPoints() {
        when(mileageTransactionRepository.sumPostedPointsByStudent(STUDENT_ID))
                .thenReturn(new BigDecimal("300"));
        List<MileageBenefitPolicy> policies = List.of(
                policy(1, "브론즈", "ALL", "0"),
                policy(2, "실버", "ALL", "300")
        );
        when(mileageBenefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        "GRADE", ACADEMIC_YEAR, List.of(SEMESTER_CODE, "ALL")))
                .thenReturn(policies);

        MileageGradeResponse response = mileageGradeService.getGrade(
                STUDENT_ID, ACADEMIC_YEAR, SEMESTER_CODE);

        assertThat(response.currentGrade().gradeName()).isEqualTo("실버");
        assertThat(response.nextGrade()).isNull();
        assertThat(response.pointsToNextGrade()).isZero();
    }

    @Test
    void getGrade_prefersSelectedSemesterPolicyWhenThresholdIsDuplicated() {
        when(mileageTransactionRepository.sumPostedPointsByStudent(STUDENT_ID))
                .thenReturn(new BigDecimal("200"));
        List<MileageBenefitPolicy> policies = List.of(
                policy(1, "연간 실버", "ALL", "100"),
                policy(2, "가을학기 실버", "FALL", "100")
        );
        when(mileageBenefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        "GRADE", ACADEMIC_YEAR, List.of(SEMESTER_CODE, "ALL")))
                .thenReturn(policies);

        MileageGradeResponse response = mileageGradeService.getGrade(
                STUDENT_ID, ACADEMIC_YEAR, SEMESTER_CODE);

        assertThat(response.currentGrade().gradeName()).isEqualTo("가을학기 실버");
    }

    @Test
    void getGrade_returnsEmptyGradeStateWhenNoGradePolicyExists() {
        when(mileageTransactionRepository.sumPostedPointsByStudent(STUDENT_ID))
                .thenReturn(null);
        when(mileageBenefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        "GRADE", ACADEMIC_YEAR, List.of(SEMESTER_CODE, "ALL")))
                .thenReturn(List.of());

        MileageGradeResponse response = mileageGradeService.getGrade(
                STUDENT_ID, ACADEMIC_YEAR, SEMESTER_CODE);

        assertThat(response.cumulativePoints()).isZero();
        assertThat(response.currentGrade()).isNull();
        assertThat(response.nextGrade()).isNull();
        assertThat(response.pointsToNextGrade()).isZero();
    }

    @Test
    void getGrade_rejectsAllSemesterAsSelectedPeriod() {
        assertThatThrownBy(() -> mileageGradeService.getGrade(STUDENT_ID, ACADEMIC_YEAR, "ALL"))
                .isInstanceOf(BusinessException.class);
    }

    private MileageBenefitPolicy policy(
            Integer id,
            String name,
            String semesterCode,
            String minimumPoints
        ) {
        MileageBenefitPolicy policy = mock(MileageBenefitPolicy.class);
        lenient().when(policy.getBenefitPolicyId()).thenReturn(id);
        lenient().when(policy.getBenefitName()).thenReturn(name);
        lenient().when(policy.getSemesterCode()).thenReturn(semesterCode);
        lenient().when(policy.getMinimumPoints()).thenReturn(new BigDecimal(minimumPoints));
        return policy;
    }
}
