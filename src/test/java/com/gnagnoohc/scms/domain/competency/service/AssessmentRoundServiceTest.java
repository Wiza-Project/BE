package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentRoundRequest;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentRoundServiceTest {

    @Mock
    AssessmentRoundRepository assessmentRoundRepository;

    @Mock
    AssessmentAttemptRepository assessmentAttemptRepository;

    @InjectMocks
    AssessmentRoundService assessmentRoundService;

    // 테스트에서 IDENTITY 채번 없이도 assessmentRoundId를 세팅하기 위한 리플렉션 헬퍼(엔티티에 세터가 없으므로)
    private static void setRoundId(AssessmentRound round, Integer roundId) {
        try {
            Field field = AssessmentRound.class.getDeclaredField("assessmentRoundId");
            field.setAccessible(true);
            field.set(round, roundId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static AssessmentRoundRequest buildRequest(Instant startsAt, Instant endsAt, Map<String, Object> targetCondition) {
        return new AssessmentRoundRequest("2026학년도 1학기 사전진단", 2026, "SPRING", "PRE", startsAt, endsAt, targetCondition);
    }

    @Test
    void registerRound_savesRoundWithConvertedTargetCondition() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentType(2026, "SPRING", "PRE"))
                .thenReturn(Optional.empty());
        when(assessmentRoundRepository.save(any(AssessmentRound.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentRoundResponse response = assessmentRoundService.registerRound(
                buildRequest(startsAt, endsAt, Map.of("grade", 3)), 1);

        assertThat(response.assessmentName()).isEqualTo("2026학년도 1학기 사전진단");
        assertThat(response.academicYear()).isEqualTo(2026);
        assertThat(response.assessmentType()).isEqualTo("PRE");
        assertThat(response.roundStatus()).isEqualTo("DRAFT");
        assertThat(response.targetCondition()).containsEntry("grade", 3);
    }

    @Test
    void registerRound_whenTargetConditionNull_meansAllStudents() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentType(2026, "SPRING", "PRE"))
                .thenReturn(Optional.empty());
        when(assessmentRoundRepository.save(any(AssessmentRound.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentRoundResponse response = assessmentRoundService.registerRound(
                buildRequest(startsAt, endsAt, null), 1);

        assertThat(response.targetCondition()).isNull();
    }

    @Test
    void registerRound_whenDuplicatePeriodAndType_throwsDuplicateAssessmentRound() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentType(2026, "SPRING", "PRE"))
                .thenReturn(Optional.of(AssessmentRound.create(
                        "기존 회차", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1)));

        assertThatThrownBy(() -> assessmentRoundService.registerRound(buildRequest(startsAt, endsAt, null), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_ASSESSMENT_ROUND);

        verify(assessmentRoundRepository, never()).save(any());
    }

    // 사전 중복검사(validateNoDuplicate)를 통과한 직후 동시 요청이 먼저 저장되는 레이스 상황을
    // save()가 uq_assessment_round_period_type 위반으로 던지는 상황으로 재현.
    @Test
    void registerRound_whenConcurrentDuplicateViolatesUniqueConstraint_throwsDuplicateAssessmentRound() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentType(2026, "SPRING", "PRE"))
                .thenReturn(Optional.empty());
        when(assessmentRoundRepository.save(any(AssessmentRound.class)))
                .thenThrow(new DataIntegrityViolationException("uq_assessment_round_period_type"));

        assertThatThrownBy(() -> assessmentRoundService.registerRound(buildRequest(startsAt, endsAt, null), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_ASSESSMENT_ROUND);
    }

    // 제약명이 uq_assessment_round_period_type이 아닌 무결성 위반(예: created_by NOT NULL)은
    // 중복 회차로 둔갑시키지 않고 원래 예외 그대로 다시 던져야 한다.
    @Test
    void registerRound_whenUnrelatedIntegrityViolation_rethrowsOriginalException() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        DataIntegrityViolationException notNullViolation = new DataIntegrityViolationException(
                "null value in column \"created_by\" violates not-null constraint");
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentType(2026, "SPRING", "PRE"))
                .thenReturn(Optional.empty());
        when(assessmentRoundRepository.save(any(AssessmentRound.class))).thenThrow(notNullViolation);

        assertThatThrownBy(() -> assessmentRoundService.registerRound(buildRequest(startsAt, endsAt, null), 1))
                .isSameAs(notNullViolation);
    }

    @Test
    void registerRound_whenStartsAtNotBeforeEndsAt_throwsInvalidAssessmentPeriod() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> assessmentRoundService.registerRound(buildRequest(startsAt, endsAt, null), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ASSESSMENT_PERIOD);

        verify(assessmentRoundRepository, never()).save(any());
    }

    @Test
    void updateRound_whenNotStarted_updatesFields() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        AssessmentRound round = AssessmentRound.create("초안", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        setRoundId(round, 100);

        when(assessmentRoundRepository.findById(100)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(100))
                .thenReturn(false);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentTypeAndAssessmentRoundIdNot(
                2026, "SPRING", "PRE", 100)).thenReturn(Optional.empty());

        Instant newEndsAt = endsAt.plus(3, ChronoUnit.DAYS);
        AssessmentRoundResponse response = assessmentRoundService.updateRound(
                100, buildRequest(startsAt, newEndsAt, Map.of("major", "컴퓨터공학과")));

        assertThat(response.endsAt()).isEqualTo(newEndsAt);
        assertThat(response.targetCondition()).containsEntry("major", "컴퓨터공학과");
    }

    // update()는 관리 중인 엔티티만 변경하므로 saveAndFlush에서 유니크 제약 위반을 재현.
    @Test
    void updateRound_whenConcurrentDuplicateViolatesUniqueConstraint_throwsDuplicateAssessmentRound() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        AssessmentRound round = AssessmentRound.create("초안", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        setRoundId(round, 100);

        when(assessmentRoundRepository.findById(100)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(100))
                .thenReturn(false);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentTypeAndAssessmentRoundIdNot(
                2026, "SPRING", "PRE", 100)).thenReturn(Optional.empty());
        when(assessmentRoundRepository.saveAndFlush(round))
                .thenThrow(new DataIntegrityViolationException("uq_assessment_round_period_type"));

        assertThatThrownBy(() -> assessmentRoundService.updateRound(100, buildRequest(startsAt, endsAt, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_ASSESSMENT_ROUND);
    }

    @Test
    void updateRound_whenUnrelatedIntegrityViolation_rethrowsOriginalException() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        AssessmentRound round = AssessmentRound.create("초안", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        setRoundId(round, 100);
        DataIntegrityViolationException notNullViolation = new DataIntegrityViolationException(
                "null value in column \"assessment_name\" violates not-null constraint");

        when(assessmentRoundRepository.findById(100)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(100))
                .thenReturn(false);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentTypeAndAssessmentRoundIdNot(
                2026, "SPRING", "PRE", 100)).thenReturn(Optional.empty());
        when(assessmentRoundRepository.saveAndFlush(round)).thenThrow(notNullViolation);

        assertThatThrownBy(() -> assessmentRoundService.updateRound(100, buildRequest(startsAt, endsAt, null)))
                .isSameAs(notNullViolation);
    }

    @Test
    void updateRound_whenAlreadyStarted_throwsAssessmentRoundNotEditable() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        AssessmentRound round = AssessmentRound.create("초안", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        setRoundId(round, 100);

        when(assessmentRoundRepository.findById(100)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(100))
                .thenReturn(true);

        assertThatThrownBy(() -> assessmentRoundService.updateRound(100, buildRequest(startsAt, endsAt, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_EDITABLE);
    }

    @Test
    void updateRound_whenRoundNotFound_throwsAssessmentRoundNotFound() {
        when(assessmentRoundRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentRoundService.updateRound(
                999, buildRequest(Instant.now(), Instant.now().plusSeconds(60), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
    }
}
