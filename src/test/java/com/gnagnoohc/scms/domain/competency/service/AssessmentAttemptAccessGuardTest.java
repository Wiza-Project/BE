package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentAttemptAccessGuardTest {

    private static final Integer STUDENT_ID = 1;
    private static final Integer ATTEMPT_ID = 10;
    private static final Integer ROUND_ID = 20;

    @Mock
    AssessmentAttemptRepository assessmentAttemptRepository;

    @InjectMocks
    AssessmentAttemptAccessGuard guard;

    private static <T> T newInstance(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static AppUser buildStudent(Integer userId) throws ReflectiveOperationException {
        AppUser student = newInstance(AppUser.class);
        ReflectionTestUtils.setField(student, "userId", userId);
        return student;
    }

    private static AssessmentRound buildRound(Instant startsAt, Instant endsAt) {
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", ROUND_ID);
        return round;
    }

    private static AssessmentAttempt buildAttempt(AssessmentRound round, AppUser student) throws ReflectiveOperationException {
        AssessmentAttempt attempt = newInstance(AssessmentAttempt.class);
        ReflectionTestUtils.setField(attempt, "attemptId", ATTEMPT_ID);
        ReflectionTestUtils.setField(attempt, "assessmentRound", round);
        ReflectionTestUtils.setField(attempt, "student", student);
        return attempt;
    }

    @Test
    void getOwnAttempt_whenOwnedByStudent_returnsAttempt() throws Exception {
        AssessmentRound round = buildRound(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        when(assessmentAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        AssessmentAttempt result = guard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID);

        assertThat(result).isSameAs(attempt);
    }

    @Test
    void getOwnAttempt_whenNotFound_throwsAssessmentAttemptNotFound() {
        when(assessmentAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
    }

    // 다른 학생의 attempt에 접근하는 경우와 존재하지 않는 경우를 같은 에러코드로 묶는다
    // (attempt 존재 여부·소유권을 응답으로 노출하지 않기 위함) — getOwnAttempt의 핵심 규칙이라 직접 검증한다.
    @Test
    void getOwnAttempt_whenNotOwnedByStudent_throwsSameErrorAsNotFound() throws Exception {
        AssessmentRound round = buildRound(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(999));
        when(assessmentAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> guard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
    }

    @Test
    void assertNotSubmitted_whenSubmittedAtIsSet_throwsDiagnosisAlreadySubmitted() throws Exception {
        AssessmentRound round = buildRound(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        ReflectionTestUtils.setField(attempt, "submittedAt", Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> guard.assertNotSubmitted(attempt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED);
    }

    @Test
    void assertPeriodOpen_whenBeforeStart_throwsDiagnosisPeriodClosed() throws Exception {
        AssessmentRound round = buildRound(Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(8, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        assertThatThrownBy(() -> guard.assertPeriodOpen(attempt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
    }

    @Test
    void assertPeriodOpen_whenAfterEnd_throwsDiagnosisPeriodClosed() throws Exception {
        AssessmentRound round = buildRound(Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        assertThatThrownBy(() -> guard.assertPeriodOpen(attempt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
    }
}
