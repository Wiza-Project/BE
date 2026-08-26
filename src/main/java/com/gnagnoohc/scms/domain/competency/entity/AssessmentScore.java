package com.gnagnoohc.scms.domain.competency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Getter
@Table(name = "assessment_score", uniqueConstraints = @UniqueConstraint(
        name = "uq_assessment_score_attempt_competency", columnNames = {"attempt_id", "competency_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assessment_score_id", nullable = false) private Integer assessmentScoreId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attempt_id", nullable = false) private AssessmentAttempt attempt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competency_id", nullable = false) private Competency competency;
    @Column(name = "raw_score", nullable = false, precision = 10, scale = 2) private BigDecimal rawScore;
    @Column(name = "converted_score", nullable = false, precision = 10, scale = 2) private BigDecimal convertedScore;
    @Column(name = "percentile", precision = 6, scale = 3) private BigDecimal percentile;
    @Column(name = "calculated_at", nullable = false) private Instant calculatedAt = Instant.now();

    // 값은 AssessmentScoreCalculator가 계산해서 넘긴다 — 이 팩토리는 엔티티 wiring만 담당(가중치 산식이 바뀌어도 여기는 안 바뀜).
    // percentile은 회차 종료 후 백분위 산출 배치가 채우므로 제출 시점엔 null로 남긴다.
    public static AssessmentScore create(AssessmentAttempt attempt, Competency competency,
                                          BigDecimal rawScore, BigDecimal convertedScore) {
        AssessmentScore score = new AssessmentScore();
        score.attempt = attempt;
        score.competency = competency;
        score.rawScore = rawScore;
        score.convertedScore = convertedScore;
        return score;
    }

    // 회차 종료 후 백분위 산출 배치(AssessmentPercentileBatchService)가 전체 응시자 점수를 모아 채운다.
    // 제출 시점엔 다른 응시자 점수가 아직 안 갖춰져 계산 불가하므로 create()에는 없다.
    public void applyPercentile(BigDecimal percentile) {
        this.percentile = percentile;
    }
}
