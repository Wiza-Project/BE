package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentAttemptResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentIntroResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentIntroServiceTest {

    @Mock AssessmentRoundRepository assessmentRoundRepository;
    @Mock AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    @Mock AssessmentAttemptRepository assessmentAttemptRepository;
    @Mock AppUserRepository appUserRepository;
    @Mock ConsentVerifier consentVerifier;

    @InjectMocks
    AssessmentIntroService assessmentIntroService;

    private static final Integer ROUND_ID = 100;
    private static final Integer STUDENT_ID = 1;

    // 테스트에서 IDENTITY 채번 없이도 id를 세팅하기 위한 리플렉션 헬퍼(엔티티에 세터가 없으므로)
    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static AssessmentRound buildRound(Instant startsAt, Instant endsAt) {
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        setField(round, "assessmentRoundId", ROUND_ID);
        return round;
    }

    @Test
    void getIntro_whenNoExistingAttempt_returnsInfoWithoutAttempt() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentRoundQuestionRepository.countByAssessmentRound_AssessmentRoundId(ROUND_ID)).thenReturn(90L);
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        AssessmentIntroResponse response = assessmentIntroService.getIntro(ROUND_ID, STUDENT_ID);

        assertThat(response.assessmentRoundId()).isEqualTo(ROUND_ID);
        assertThat(response.questionCount()).isEqualTo(90);
        assertThat(response.estimatedMinutes()).isEqualTo(30); // 90문항 * 20초 = 1800초 = 30분
        assertThat(response.attemptId()).isNull();
        assertThat(response.attemptStatus()).isNull();
    }

    @Test
    void getIntro_whenAlreadyStarted_returnsExistingAttemptInfo() {
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(7, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);
        AppUser student = mock(AppUser.class);
        AssessmentAttempt attempt = AssessmentAttempt.create(round, student, null);
        setField(attempt, "attemptId", 500);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentRoundQuestionRepository.countByAssessmentRound_AssessmentRoundId(ROUND_ID)).thenReturn(90L);
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.of(attempt));

        AssessmentIntroResponse response = assessmentIntroService.getIntro(ROUND_ID, STUDENT_ID);

        assertThat(response.attemptId()).isEqualTo(500);
        assertThat(response.attemptStatus()).isEqualTo("NOT_STARTED");
    }

    @Test
    void getIntro_whenRoundNotFound_throwsAssessmentRoundNotFound() {
        when(assessmentRoundRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentIntroService.getIntro(999, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
    }

    @Test
    void startAttempt_whenWithinPeriodAndConsentAgreed_createsAttemptLinkedToSensitiveInfoConsent() {
        Instant startsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().plus(7, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);
        AppUser student = mock(AppUser.class);
        UserConsent sensitiveConsent = mock(UserConsent.class);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.empty());
        when(consentVerifier.hasAgreedAllRequired(eq(STUDENT_ID), eq(ConsentModuleCode.ASSESSMENT), any(Instant.class)))
                .thenReturn(true);
        when(consentVerifier.findCurrentValidConsent(
                eq(STUDENT_ID), eq(ConsentModuleCode.ASSESSMENT), eq(ConsentType.SENSITIVE_INFO), any(Instant.class)))
                .thenReturn(Optional.of(sensitiveConsent));
        when(appUserRepository.getReferenceById(STUDENT_ID)).thenReturn(student);
        when(assessmentAttemptRepository.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAttemptResponse response = assessmentIntroService.startAttempt(ROUND_ID, STUDENT_ID);

        assertThat(response.attemptStatus()).isEqualTo("NOT_STARTED");
        verify(assessmentAttemptRepository).save(any(AssessmentAttempt.class));
        // PERSONAL_INFO 대체 조회로 넘어가지 않아야 함 (SENSITIVE_INFO를 이미 찾았으므로)
        verify(consentVerifier, never()).findCurrentValidConsent(
                eq(STUDENT_ID), eq(ConsentModuleCode.ASSESSMENT), eq(ConsentType.PERSONAL_INFO), any(Instant.class));
    }

    @Test
    void startAttempt_whenSensitiveInfoConsentMissing_fallsBackToPersonalInfoConsent() {
        Instant startsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().plus(7, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);
        AppUser student = mock(AppUser.class);
        UserConsent personalInfoConsent = mock(UserConsent.class);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.empty());
        when(consentVerifier.hasAgreedAllRequired(eq(STUDENT_ID), eq(ConsentModuleCode.ASSESSMENT), any(Instant.class)))
                .thenReturn(true);
        when(consentVerifier.findCurrentValidConsent(
                eq(STUDENT_ID), eq(ConsentModuleCode.ASSESSMENT), eq(ConsentType.SENSITIVE_INFO), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(consentVerifier.findCurrentValidConsent(
                eq(STUDENT_ID), eq(ConsentModuleCode.ASSESSMENT), eq(ConsentType.PERSONAL_INFO), any(Instant.class)))
                .thenReturn(Optional.of(personalInfoConsent));
        when(appUserRepository.getReferenceById(STUDENT_ID)).thenReturn(student);
        when(assessmentAttemptRepository.save(any(AssessmentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentAttemptResponse response = assessmentIntroService.startAttempt(ROUND_ID, STUDENT_ID);

        assertThat(response.attemptStatus()).isEqualTo("NOT_STARTED");
        verify(assessmentAttemptRepository).save(any(AssessmentAttempt.class));
    }

    @Test
    void startAttempt_whenAlreadyStarted_returnsExistingAttemptWithoutCreatingNewOne() {
        Instant startsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().plus(7, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);
        AppUser student = mock(AppUser.class);
        AssessmentAttempt existing = AssessmentAttempt.create(round, student, null);
        setField(existing, "attemptId", 500);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.of(existing));

        AssessmentAttemptResponse response = assessmentIntroService.startAttempt(ROUND_ID, STUDENT_ID);

        assertThat(response.attemptId()).isEqualTo(500);
        verify(assessmentAttemptRepository, never()).save(any());
        verify(consentVerifier, never()).hasAgreedAllRequired(any(), any(), any());
    }

    @Test
    void startAttempt_whenBeforePeriodStarts_throwsDiagnosisPeriodClosed() {
        Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().plus(7, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentIntroService.startAttempt(ROUND_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);

        verify(assessmentAttemptRepository, never()).save(any());
    }

    @Test
    void startAttempt_whenAfterPeriodEnds_throwsDiagnosisPeriodClosed() {
        Instant startsAt = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentIntroService.startAttempt(ROUND_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
    }

    @Test
    void startAttempt_whenRequiredConsentNotAgreed_throwsRequiredConsentNotAgreed() {
        Instant startsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().plus(7, ChronoUnit.DAYS);
        AssessmentRound round = buildRound(startsAt, endsAt);

        when(assessmentRoundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(ROUND_ID, STUDENT_ID))
                .thenReturn(Optional.empty());
        when(consentVerifier.hasAgreedAllRequired(eq(STUDENT_ID), eq(ConsentModuleCode.ASSESSMENT), any(Instant.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> assessmentIntroService.startAttempt(ROUND_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);

        verify(assessmentAttemptRepository, never()).save(any());
    }

    @Test
    void startAttempt_whenRoundNotFound_throwsAssessmentRoundNotFound() {
        when(assessmentRoundRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentIntroService.startAttempt(999, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
    }
}
