package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// 백분위 산식도 학교 정책에 따라 바뀔 수 있어 엔티티가 아니라 별도 도메인 서비스로 분리한다
// (AssessmentScoreCalculator와 같은 이유, package-info.java 체크리스트 참고).
@Component
public class AssessmentPercentileCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int DIVIDE_SCALE = 10;
    private static final int PERCENTILE_SCALE = 3;

    // 회차 하나의 전체 assessment_score를 역량(competency)별로 나눠 "내 환산점수 이하인 응시자 비율"로
    // 백분위를 매긴다. 응시자가 1명뿐인 역량은 자기 자신만 이하이므로 100.000이 나오는데, 이는 제출 즉시
    // 계산을 미룬 이유(첫 응시자가 항상 100%)와 수치상 같아 보이지만 이 값은 회차 종료(전원 응시 완료) 후
    // 계산된 것이므로 유효하다.
    public Map<Integer, BigDecimal> calculate(List<AssessmentScore> scores) {
        Map<Integer, List<AssessmentScore>> byCompetencyId = scores.stream()
                .collect(Collectors.groupingBy(s -> s.getCompetency().getCompetencyId()));

        return byCompetencyId.values().stream()
                .flatMap(group -> calculateForCompetency(group).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // convertedScore별 "그 값 이하인 응시자 수(누적 도수)"를 한 번만 계산해 재사용한다 — O(n log n).
    // 응시자마다 그룹 전체를 매번 다시 스캔하면(O(n^2)) 학년 전체 대상 회차(응시자 수천 명)에서 배치 한 번의
    // 연산량이 응시자 수의 제곱으로 커지고, 이 배치는 트랜잭션 하나 안에서 도니 DB 점유 시간도 같이 늘어난다.
    // TreeMap을 쓰는 이유: convertedScore는 BigDecimal이라 scale이 다르면 equals()가 어긋나는 HashMap과
    // 달리, TreeMap은 compareTo() 기준으로 키를 비교·정렬해 이런 스케일 불일치에 안전하다.
    private Map<Integer, BigDecimal> calculateForCompetency(List<AssessmentScore> group) {
        int total = group.size();

        TreeMap<BigDecimal, Long> countByScore = group.stream()
                .collect(Collectors.groupingBy(AssessmentScore::getConvertedScore, TreeMap::new, Collectors.counting()));

        Map<BigDecimal, Long> cumulativeCountByScore = new TreeMap<>();
        long cumulative = 0;
        for (Map.Entry<BigDecimal, Long> entry : countByScore.entrySet()) {
            cumulative += entry.getValue();
            cumulativeCountByScore.put(entry.getKey(), cumulative);
        }

        return group.stream().collect(Collectors.toMap(
                AssessmentScore::getAssessmentScoreId,
                score -> toPercentile(cumulativeCountByScore.get(score.getConvertedScore()), total)));
    }

    private BigDecimal toPercentile(long countLessOrEqual, int total) {
        return BigDecimal.valueOf(countLessOrEqual)
                .divide(BigDecimal.valueOf(total), DIVIDE_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PERCENTILE_SCALE, RoundingMode.HALF_UP);
    }
}
