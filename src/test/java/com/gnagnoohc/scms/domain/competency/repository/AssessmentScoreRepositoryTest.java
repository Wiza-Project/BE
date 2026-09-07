package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * nullifyNonEnrolledPercentiles가 실제 DB에서 의도한 행만 지우는지 검증한다.
 * 백필 러너 단위 테스트(AssessmentPercentileBackfillProcessorTest)는 이 리포지토리를 목킹하므로,
 * 다단계 암시 조인(assessment_score -> assessment_attempt -> app_user)과 '재학'/'STUDENT' 리터럴,
 * academic_status IS NULL 분기가 실제로 맞게 도는지는 여기서만 확인된다.
 */
@SpringBootTest
@Transactional
class AssessmentScoreRepositoryTest {

    private static final BigDecimal SEED_PERCENTILE = new BigDecimal("42.000");

    @Autowired
    private AssessmentScoreRepository assessmentScoreRepository;
    @Autowired
    private AssessmentRoundRepository assessmentRoundRepository;
    @Autowired
    private AssessmentAttemptRepository assessmentAttemptRepository;
    @Autowired
    private CompetencyRepository competencyRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void nullifyNonEnrolledPercentiles_clearsOnlyNonEnrolledScoresInTheRound() {
        Competency competency = saveCompetency();
        AssessmentRound round = saveCompletedRound("2026학년도 1학기 사전진단", "PRE");
        AssessmentRound otherRound = saveCompletedRound("2026학년도 1학기 사후진단", "POST");

        AssessmentScore enrolled = saveScore(round, competency, saveUser("STUDENT", "재학"));
        AssessmentScore graduated = saveScore(round, competency, saveUser("STUDENT", "졸업"));
        AssessmentScore statusNull = saveScore(round, competency, saveUser("STUDENT", null));
        AssessmentScore nonStudent = saveScore(round, competency, saveUser("STAFF", "재학"));
        // 다른 회차의 비재학생 점수는 roundId 스코프 밖이라 건드리면 안 된다.
        AssessmentScore otherRoundGraduated = saveScore(otherRound, competency, saveUser("STUDENT", "졸업"));

        entityManager.flush();
        entityManager.clear();

        int updated = assessmentScoreRepository.nullifyNonEnrolledPercentiles(round.getAssessmentRoundId());

        assertThat(updated).isEqualTo(3);
        assertThat(percentileOf(enrolled)).isEqualByComparingTo(SEED_PERCENTILE);
        assertThat(percentileOf(graduated)).isNull();
        assertThat(percentileOf(statusNull)).isNull();
        assertThat(percentileOf(nonStudent)).isNull();
        assertThat(percentileOf(otherRoundGraduated)).isEqualByComparingTo(SEED_PERCENTILE);
    }

    private BigDecimal percentileOf(AssessmentScore score) {
        return assessmentScoreRepository.findById(score.getAssessmentScoreId()).orElseThrow().getPercentile();
    }

    private Competency saveCompetency() {
        return competencyRepository.save(
                Competency.createTop("C" + System.nanoTime(), "문제해결역량", null, null, 1, 1));
    }

    private AssessmentRound saveCompletedRound(String name, String assessmentType) {
        Instant endsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        AssessmentRound round = AssessmentRound.create(
                name, 2026, "SPRING", assessmentType, endsAt.minus(7, ChronoUnit.DAYS), endsAt, null, 1);
        round.completePercentileCalculation();
        return assessmentRoundRepository.save(round);
    }

    private AppUser saveUser(String userType, String academicStatus) {
        AppUser user = BeanUtils.instantiateClass(AppUser.class);
        ReflectionTestUtils.setField(user, "universityNo", "BF" + System.nanoTime());
        ReflectionTestUtils.setField(user, "userName", "백필테스트");
        ReflectionTestUtils.setField(user, "userType", userType);
        ReflectionTestUtils.setField(user, "passwordHash", "$2a$10$dummyhashedpasswordfortest");
        ReflectionTestUtils.setField(user, "email", "backfill" + System.nanoTime() + "@univ.ac.kr");
        ReflectionTestUtils.setField(user, "accountStatus", "ACTIVE");
        ReflectionTestUtils.setField(user, "academicStatus", academicStatus);
        return appUserRepository.save(user);
    }

    private AssessmentScore saveScore(AssessmentRound round, Competency competency, AppUser student) {
        AssessmentAttempt attempt = assessmentAttemptRepository.save(
                AssessmentAttempt.create(round, student, null));
        AssessmentScore score = AssessmentScore.create(
                attempt, competency, BigDecimal.valueOf(10), BigDecimal.valueOf(80));
        score.applyPercentile(SEED_PERCENTILE);
        return assessmentScoreRepository.save(score);
    }
}
