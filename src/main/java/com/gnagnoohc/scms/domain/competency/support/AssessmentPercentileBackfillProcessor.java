package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.domain.competency.service.AssessmentPercentileCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 회차 하나의 개인 백분위를 재학생 한정 모수(AssessmentTargetPolicy.ENROLLED_STUDENT) 기준으로 다시 매기는
 * 트랜잭션 단위. {@link AssessmentPercentileBackfillRunner}가 완료 회차마다 이 메서드를 호출한다.
 *
 * <p>한 회차 실패가 나머지 회차 재계산을 롤백하지 않도록 {@code REQUIRES_NEW}로 분리하고, 러너와
 * 실행부를 서로 다른 빈으로 나눈다 — 같은 클래스 자기호출은 Spring AOP 프록시를 우회해 트랜잭션이
 * 새로 열리지 않는다({@code ResumeCompetencySnapshotUpsertService}와 같은 이유).</p>
 *
 * <p>{@code round_status}는 건드리지 않는다 — 결과 화면 노출 여부(percentileAvailable), 늦은 제출 차단,
 * 배치 재진입 가드가 이 값을 읽으므로 백필이 잠깐이라도 뒤집으면 안 된다.
 * {@code assessment_score.percentile}만 바꾼다.</p>
 */
@Component
@RequiredArgsConstructor
public class AssessmentPercentileBackfillProcessor {

    private final AssessmentScoreQueryRepository assessmentScoreQueryRepository;
    private final AssessmentScoreRepository assessmentScoreRepository;
    private final AssessmentPercentileCalculator assessmentPercentileCalculator;

    /** 한 회차의 재계산 결과 집계. */
    public record RoundResult(int recalculatedScoreRows, int nulledScoreRows, boolean hadNoEnrolledScores) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RoundResult recalculateRound(Integer roundId) {
        // 비재학생 점수의 percentile을 먼저 NULL로 비운다 — 재학생 점수를 dirty 상태로 만들기 전에 벌크
        // UPDATE를 끝내야 @Modifying(clearAutomatically)로 영속성 컨텍스트가 비워져도 잃을 변경이 없다.
        int nulledScoreRows = assessmentScoreRepository.nullifyNonEnrolledPercentiles(roundId);

        List<AssessmentScore> enrolledScores =
                assessmentScoreQueryRepository.findEnrolledScoresByRoundIdFetchCompetency(roundId);
        if (enrolledScores.isEmpty()) {
            return new RoundResult(0, nulledScoreRows, true);
        }

        Map<Integer, BigDecimal> percentileByScoreId = assessmentPercentileCalculator.calculate(enrolledScores);
        for (AssessmentScore score : enrolledScores) {
            score.applyPercentile(percentileByScoreId.get(score.getAssessmentScoreId()));
        }
        assessmentScoreRepository.saveAll(enrolledScores);

        return new RoundResult(enrolledScores.size(), nulledScoreRows, false);
    }
}
