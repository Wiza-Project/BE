package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 역량별 가중치 산식은 학교 정책에 따라 바뀔 수 있어 엔티티가 아니라 이 도메인 서비스로 분리한다(package-info.java 체크리스트).
// 완결성(모든 문항 응답 여부) 검증은 이 클래스의 책임이 아니라 호출자(AssessmentSubmissionService)의 책임이다.
@Component
public class AssessmentScoreCalculator {

    private static final BigDecimal LIKERT_MAX = BigDecimal.valueOf(5);
    private static final BigDecimal REVERSE_BASE = BigDecimal.valueOf(6);
    private static final BigDecimal CONVERTED_SCALE = BigDecimal.valueOf(100);

    public record CompetencyScore(Competency competency, BigDecimal rawScore, BigDecimal convertedScore) {}

    public List<CompetencyScore> calculate(List<AssessmentRoundQuestion> roundQuestions,
                                            Map<Integer, BigDecimal> selectedValuesByQuestionId) {
        Map<Integer, List<AssessmentRoundQuestion>> byCompetencyId = roundQuestions.stream()
                .collect(Collectors.groupingBy(rq -> rq.getQuestion().getCompetency().getCompetencyId()));

        // groupingBy는 HashMap을 반환해 values() 순회 순서가 competencyId/displayOrder와 무관하다.
        // scores는 방사형 차트 축 순서(Competency.displayOrder)를 그대로 따라야 하므로 명시적으로 정렬한다.
        return byCompetencyId.values().stream()
                .map(questions -> calculateForCompetency(questions, selectedValuesByQuestionId))
                .sorted(Comparator.comparing(s -> s.competency().getDisplayOrder()))
                .toList();
    }

    private CompetencyScore calculateForCompetency(List<AssessmentRoundQuestion> questions,
                                                     Map<Integer, BigDecimal> selectedValuesByQuestionId) {
        Competency competency = questions.get(0).getQuestion().getCompetency();

        BigDecimal sum = BigDecimal.ZERO;
        for (AssessmentRoundQuestion rq : questions) {
            AssessmentQuestion question = rq.getQuestion();
            BigDecimal selected = selectedValuesByQuestionId.get(question.getQuestionId());
            BigDecimal effective = question.isReverse() ? REVERSE_BASE.subtract(selected) : selected;
            sum = sum.add(effective);
        }

        BigDecimal rawScore = sum.divide(BigDecimal.valueOf(questions.size()), 2, RoundingMode.HALF_UP);
        // convertedScore는 반올림 전 sum이 아니라 이미 소수 2자리로 반올림된 rawScore를 기준으로 계산한다.
        // sum 기준으로 다시 계산하면 화면에 같이 노출되는 rawScore·convertedScore 두 숫자가 서로
        // 정확히 들어맞지 않는 것처럼 보일 수 있어(예: rawScore=2.33인데 convertedScore가 46.67로 어긋남),
        // 두 값의 정합성을 우선하고 아주 미세한 정밀도 손실은 감수한다.
        BigDecimal convertedScore = rawScore
                .divide(LIKERT_MAX, 10, RoundingMode.HALF_UP)
                .multiply(CONVERTED_SCALE)
                .setScale(2, RoundingMode.HALF_UP);

        return new CompetencyScore(competency, rawScore, convertedScore);
    }
}
