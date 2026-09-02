package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse.CompetencyResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 진단 결과에서 "추천 대상이 될 취약 역량"을 골라내는 규칙. 추천 서비스 본체에서 분리한 이유는
 * 점수 산식·백분위와 마찬가지로 "몇 개를, 무슨 기준으로 취약이라 볼지"가 학교 정책에 따라 바뀔 값이라서다
 * (competency package-info.java 체크리스트와 같은 근거). 지금은 가장 단순한 규칙만 둔다.
 */
@Component
public class WeakCompetencySelector {

    /**
     * 취약 역량으로 뽑을 개수. 기획서에 값이 명시돼 있지 않아 BE가 정했다(FE 공유 필요).
     * 방사형 차트 6축 중 하위 2축을 집중 보완 대상으로 본다는 의미.
     */
    private static final int WEAK_COMPETENCY_COUNT = 2;

    /**
     * 환산점수(convertedScore) 오름차순으로 하위 N개 역량을 반환한다. 동점이면 방사형 차트 축 순서
     * (displayOrder)가 앞선 역량을 먼저 놓아 결과가 결정적이게 한다. 백분위는 회차 집계 완료 전이면
     * null이라 기준으로 쓰지 않는다 — 제출만 됐으면 항상 존재하는 convertedScore로만 판정한다.
     * 환산점수가 없는 항목(정상 경로에선 나올 수 없지만 데이터 이상 방어)은 후보에서 제외한다.
     */
    public List<CompetencyResult> select(List<CompetencyResult> scores) {
        return scores.stream()
                .filter(score -> score.convertedScore() != null)
                .sorted(Comparator.comparing(CompetencyResult::convertedScore)
                        .thenComparing(CompetencyResult::displayOrder,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(WEAK_COMPETENCY_COUNT)
                .toList();
    }
}
