package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultReadyEvent;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.service.CommonCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentResultReadyEventPublisherTest {

    private static final Integer STUDENT_ID = 7;
    private static final Integer ATTEMPT_ID = 10;
    private static final Integer ROUND_ID = 20;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    AssessmentAttemptRepository assessmentAttemptRepository;

    @Mock
    AssessmentScoreRepository assessmentScoreRepository;

    @Mock
    CommonCodeService commonCodeService;

    @InjectMocks
    AssessmentResultReadyEventPublisher publisher;

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

    private static AssessmentRound buildRound() {
        Instant now = Instant.now();
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE",
                now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null, 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", ROUND_ID);
        return round;
    }

    private static AssessmentAttempt buildAttempt(AssessmentRound round, AppUser student) throws ReflectiveOperationException {
        AssessmentAttempt attempt = newInstance(AssessmentAttempt.class);
        ReflectionTestUtils.setField(attempt, "attemptId", ATTEMPT_ID);
        ReflectionTestUtils.setField(attempt, "assessmentRound", round);
        ReflectionTestUtils.setField(attempt, "student", student);
        ReflectionTestUtils.setField(attempt, "submittedAt", Instant.parse("2026-03-02T00:00:00Z"));
        return attempt;
    }

    private static Competency buildCompetency(Integer competencyId, String name, Integer displayOrder) {
        Competency competency = Competency.createTop("C" + competencyId, name, "English", "설명", displayOrder, 1);
        ReflectionTestUtils.setField(competency, "competencyId", competencyId);
        return competency;
    }

    private AssessmentResultReadyEvent capturePublished() {
        ArgumentCaptor<AssessmentResultReadyEvent> captor = ArgumentCaptor.forClass(AssessmentResultReadyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void publishFromSubmit_buildsPayloadFromRoundMetaAndCalculatedScores_withNullRequestId() throws Exception {
        AssessmentRound round = buildRound();
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        // 일부러 displayOrder 역순으로 넣어 이벤트에서 오름차순으로 재정렬되는지 확인한다.
        Competency displayedSecond = buildCompetency(100, "문제해결역량", 2);
        Competency displayedFirst = buildCompetency(200, "의사소통역량", 1);
        List<AssessmentScoreCalculator.CompetencyScore> calculated = List.of(
                new AssessmentScoreCalculator.CompetencyScore(displayedSecond, BigDecimal.valueOf(3), new BigDecimal("60.01")),
                new AssessmentScoreCalculator.CompetencyScore(displayedFirst, BigDecimal.valueOf(4), new BigDecimal("75.02")));

        when(commonCodeService.getCodeName("SEMESTER", "SPRING")).thenReturn("1학기");

        publisher.publishFromSubmit(attempt, calculated);

        AssessmentResultReadyEvent event = capturePublished();
        assertThat(event.studentId()).isEqualTo(STUDENT_ID);
        assertThat(event.attemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(event.assessmentRoundId()).isEqualTo(ROUND_ID);
        assertThat(event.assessmentName()).isEqualTo("2026학년도 1학기 사전진단");
        assertThat(event.academicYear()).isEqualTo(2026);
        assertThat(event.semesterCode()).isEqualTo("SPRING");
        assertThat(event.semesterLabel()).isEqualTo("1학기");
        assertThat(event.assessmentPhase()).isEqualTo("PRE");
        assertThat(event.submittedAt()).isEqualTo(Instant.parse("2026-03-02T00:00:00Z"));
        // (60.01 + 75.02) / 2 = 67.515 → HALF_UP scale 2 = 67.52
        assertThat(event.overallAverageScore()).isEqualByComparingTo(new BigDecimal("67.52"));
        assertThat(event.overallAverageScore().scale()).isEqualTo(2);
        assertThat(event.scores()).extracting(AssessmentResultReadyEvent.CompetencyScore::competencyId)
                .containsExactly(200, 100); // displayOrder 1인 역량이 먼저
        assertThat(event.scores()).extracting(AssessmentResultReadyEvent.CompetencyScore::displayOrder)
                .containsExactly(1, 2);
        assertThat(event.requestId()).isNull();
    }

    // 문항 0개 회차를 제출하면 계산 점수 리스트가 비는데, 전체 평균 산식이 0으로 나누기라 예외가 난다.
    // 빈 리스트면 이벤트를 발행하지 않고 조용히 넘어가야 한다(제출 트랜잭션을 깨지 않음).
    @Test
    void publishFromSubmit_whenNoScores_doesNotPublishAnyEventAndDoesNotThrow() throws Exception {
        AssessmentRound round = buildRound();
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        publisher.publishFromSubmit(attempt, List.of());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publish_loadsAttemptAndScores_andPassesThroughRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        AssessmentRound round = buildRound();
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency c1 = buildCompetency(200, "의사소통역량", 1);
        Competency c2 = buildCompetency(100, "문제해결역량", 2);
        // 리포지토리가 이미 displayOrder 오름차순으로 정렬해 돌려준다.
        List<AssessmentScore> scores = List.of(
                AssessmentScore.create(attempt, c1, BigDecimal.valueOf(4), new BigDecimal("80.00")),
                AssessmentScore.create(attempt, c2, BigDecimal.valueOf(3), new BigDecimal("60.00")));

        when(assessmentAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(ATTEMPT_ID)).thenReturn(scores);
        when(commonCodeService.getCodeName("SEMESTER", "SPRING")).thenReturn("1학기");

        publisher.publish(ATTEMPT_ID, requestId);

        AssessmentResultReadyEvent event = capturePublished();
        assertThat(event.requestId()).isEqualTo(requestId);
        assertThat(event.scores()).extracting(AssessmentResultReadyEvent.CompetencyScore::competencyId)
                .containsExactly(200, 100);
        assertThat(event.overallAverageScore()).isEqualByComparingTo(new BigDecimal("70.00")); // (80+60)/2
    }

    @Test
    void publish_whenNoScores_doesNotPublishAnyEvent() throws Exception {
        AssessmentRound round = buildRound();
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        lenient().when(commonCodeService.getCodeName(any(), any())).thenReturn("1학기");

        when(assessmentAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(ATTEMPT_ID))
                .thenReturn(List.of());

        publisher.publish(ATTEMPT_ID, null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publish_fallsBackToRawSemesterCode_whenCommonCodeMissing() throws Exception {
        AssessmentRound round = buildRound();
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency c1 = buildCompetency(200, "의사소통역량", 1);
        List<AssessmentScore> scores = List.of(
                AssessmentScore.create(attempt, c1, BigDecimal.valueOf(4), new BigDecimal("80.00")));

        when(assessmentAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(ATTEMPT_ID)).thenReturn(scores);
        // CommonCodeService.getCodeName은 매핑이 없으면 코드 원값을 그대로 돌려준다.
        when(commonCodeService.getCodeName("SEMESTER", "SPRING")).thenReturn("SPRING");

        publisher.publish(ATTEMPT_ID, null);

        assertThat(capturePublished().semesterLabel()).isEqualTo("SPRING");
    }
}
