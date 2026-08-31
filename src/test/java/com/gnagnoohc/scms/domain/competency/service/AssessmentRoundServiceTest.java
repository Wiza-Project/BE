package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentRoundRequest;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter;
import org.mockito.ArgumentCaptor;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    @Mock
    AssessmentQuestionRepository assessmentQuestionRepository;

    @Mock
    AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;

    // TargetConditionInterpreter는 의존성 없는 순수 컴포넌트라 목(mock) 대신 실제 인스턴스를 쓴다 —
    // isValidShape()를 목으로 두면 boolean 기본값(false)이 반환돼 targetCondition을 넘기는
    // 기존 테스트가 전부 INVALID_INPUT으로 깨지기 때문에, 매 테스트마다 스텁하는 대신 실제 판정 로직을 쓴다.
    AssessmentRoundService assessmentRoundService;

    @BeforeEach
    void setUp() {
        // 편성용 문항 조회는 미스텁 시 Mockito 기본값(빈 List)을 돌려주므로 등록 성공 경로에서 문항 0개로 편성된다.
        assessmentRoundService = new AssessmentRoundService(
                assessmentRoundRepository, assessmentAttemptRepository,
                assessmentQuestionRepository, assessmentRoundQuestionRepository,
                new TargetConditionInterpreter());
    }

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
                buildRequest(startsAt, endsAt, Map.of("grades", List.of(3))), 1);

        assertThat(response.assessmentName()).isEqualTo("2026학년도 1학기 사전진단");
        assertThat(response.academicYear()).isEqualTo(2026);
        assertThat(response.assessmentType()).isEqualTo("PRE");
        assertThat(response.roundStatus()).isEqualTo("DRAFT");
        assertThat(response.targetCondition()).containsEntry("grades", List.of(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerRound_composesActiveQuestionsInRowOrder() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        when(assessmentRoundRepository.findByAcademicYearAndSemesterCodeAndAssessmentType(2026, "SPRING", "PRE"))
                .thenReturn(Optional.empty());
        when(assessmentRoundRepository.save(any(AssessmentRound.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Competency competency = Competency.createTop("C100", "자기관리 역량", null, null, 100, 1);
        AssessmentQuestion first = AssessmentQuestion.createFromUpload(competency, "문항1", null, 1);
        AssessmentQuestion second = AssessmentQuestion.createFromUpload(competency, "문항2", null, 1);
        when(assessmentQuestionRepository.findAllActiveForRoundComposition())
                .thenReturn(List.of(first, second));

        assessmentRoundService.registerRound(buildRequest(startsAt, endsAt, null), 1);

        ArgumentCaptor<List<AssessmentRoundQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(assessmentRoundQuestionRepository).saveAll(captor.capture());
        List<AssessmentRoundQuestion> composed = captor.getValue();
        assertThat(composed).hasSize(2);
        assertThat(composed.get(0).getQuestion()).isSameAs(first);
        assertThat(composed.get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(composed.get(1).getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void registerRound_whenTargetConditionGradesNotArray_throwsInvalidInput() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);

        assertThatThrownBy(() -> assessmentRoundService.registerRound(
                buildRequest(startsAt, endsAt, Map.of("grades", 3)), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(assessmentRoundRepository, never()).save(any());
    }

    // 이 학교는 단과대가 없어 colleges 같은 인식 못 하는 키가 저장되는 걸 등록 시점부터 막는다
    // (TargetConditionInterpreter.hasUnrecognizedKey와 동일한 판정 재사용).
    @Test
    void registerRound_whenTargetConditionHasUnrecognizedKey_throwsInvalidInput() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);

        assertThatThrownBy(() -> assessmentRoundService.registerRound(
                buildRequest(startsAt, endsAt, Map.of("colleges", List.of("공과대학"))), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(assessmentRoundRepository, never()).save(any());
    }

    // asInt()는 true→1처럼 정수가 아닌 값도 그럴듯한 숫자로 조용히 바꿔버리므로, 배열 안 원소
    // 타입까지 등록 시점에 막아야 한다(재검토 스레드 4번 근거).
    @Test
    void registerRound_whenTargetConditionGradeElementNotIntegral_throwsInvalidInput() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);

        assertThatThrownBy(() -> assessmentRoundService.registerRound(
                buildRequest(startsAt, endsAt, Map.of("grades", List.of(true))), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(assessmentRoundRepository, never()).save(any());
    }

    // 4294971296(2^32+4000)은 isIntegralNumber()는 통과하지만 int로 캐스팅하면 4000으로
    // 오버플로우된다 — 존재하는 학과 코드ID로 조용히 둔갑하지 않도록 등록 시점에 막는다.
    @Test
    void registerRound_whenTargetConditionMajorCodeIdExceedsIntRange_throwsInvalidInput() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);

        assertThatThrownBy(() -> assessmentRoundService.registerRound(
                buildRequest(startsAt, endsAt, Map.of("majorCodeIds", List.of(4294971296L))), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(assessmentRoundRepository, never()).save(any());
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
                100, buildRequest(startsAt, newEndsAt, Map.of("majorCodeIds", List.of(8000))));

        assertThat(response.endsAt()).isEqualTo(newEndsAt);
        assertThat(response.targetCondition()).containsEntry("majorCodeIds", List.of(8000));
    }

    @Test
    void updateRound_whenTargetConditionMajorCodeIdsNotArray_throwsInvalidInput() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        AssessmentRound round = AssessmentRound.create("초안", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        setRoundId(round, 100);

        when(assessmentRoundRepository.findById(100)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(100))
                .thenReturn(false);

        assertThatThrownBy(() -> assessmentRoundService.updateRound(
                100, buildRequest(startsAt, endsAt, Map.of("majorCodeIds", 8000))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(assessmentRoundRepository, never()).saveAndFlush(any());
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
