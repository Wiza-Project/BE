package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentComparisonResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentComparisonResponse.ComparisonSide;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentComparisonResponse.CompetencyDelta;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse.CompetencyResult;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentComparisonService {

    private final AssessmentResultService assessmentResultService;
    private final AssessmentAttemptAccessGuard assessmentAttemptAccessGuard;

    public AssessmentComparisonResponse compare(Integer firstAttemptId, Integer secondAttemptId, Integer studentId) {
        if (firstAttemptId.equals(secondAttemptId)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_COMPARISON_SAME_ATTEMPT);
        }

        // 기획: "결과 조회 API를 2회 호출 + 변화량 계산 레이어만 추가". 소유권 검증(Q014)·미채점 차단(Q018)·
        // 백분위 노출 게이트는 결과 조회 로직을 그대로 재사용하고, 여기서는 쌍 검증·변화량 계산만 얹는다.
        // getResult가 응답에 회차 메타(진단명/구분/학년도/학기)를 담지 않아, 라벨용으로 attempt를 한 번 더 읽는다
        // (attempt는 소형 로우라 학생 1인의 단발 조회에서 추가 조회 비용이 미미하다).
        SideData first = load(firstAttemptId, studentId);
        SideData second = load(secondAttemptId, studentId);

        // 기획상 assessment_type(PRE/POST)이 사전·사후 비교의 기준값이다. 두 응시가 같은 학년도의
        // 사전(PRE)·사후(POST) 한 쌍이 아니면(같은 구분 2건, 학년도 불일치 등) 방향을 정할 근거가 없고
        // 계산되는 변화량도 무의미하므로 비교 자체를 거부한다. FE가 두 attemptId를 아무 순서로 넘겨도 되도록
        // 방향은 여기서 구분값으로 정한다.
        SideData before = pick(first, second, "PRE");
        SideData after = pick(first, second, "POST");
        if (before == null || after == null
                || !before.round().getAcademicYear().equals(after.round().getAcademicYear())) {
            throw new BusinessException(ErrorCode.ASSESSMENT_COMPARISON_NOT_PRE_POST_PAIR);
        }

        return new AssessmentComparisonResponse(
                ComparisonSide.of(before.result(), before.round()),
                ComparisonSide.of(after.result(), after.round()),
                buildDeltas(before.result().scores(), after.result().scores()));
    }

    private SideData load(Integer attemptId, Integer studentId) {
        AssessmentResultResponse result = assessmentResultService.getResult(attemptId, studentId);
        AssessmentRound round = assessmentAttemptAccessGuard.getOwnAttempt(attemptId, studentId).getAssessmentRound();
        return new SideData(result, round);
    }

    // 두 응시 중 주어진 구분(PRE/POST)에 해당하는 쪽을 고른다. 둘 다 같은 구분이거나 둘 다 아니면
    // null을 반환해 호출부의 쌍 검증 실패로 이어진다.
    private static SideData pick(SideData a, SideData b, String assessmentType) {
        boolean aMatch = assessmentType.equals(a.round().getAssessmentType());
        boolean bMatch = assessmentType.equals(b.round().getAssessmentType());
        if (aMatch == bMatch) {
            return null;
        }
        return aMatch ? a : b;
    }

    // 두 회차 모두 C1~C6 6개 역량이므로 정상 경로에선 1:1로 매칭된다. 한쪽에만 있는 역량이 생겨도
    // (데이터 이상) 빠뜨리지 않도록 두 쪽 역량의 합집합으로 만들되, 방사형 차트 축과 어긋나지 않게
    // displayOrder(축순서)로 정렬한다.
    private static List<CompetencyDelta> buildDeltas(List<CompetencyResult> before, List<CompetencyResult> after) {
        Map<Integer, CompetencyResult> beforeByCompetency = indexByCompetency(before);
        Map<Integer, CompetencyResult> afterByCompetency = indexByCompetency(after);

        Set<Integer> competencyIds = new LinkedHashSet<>(beforeByCompetency.keySet());
        competencyIds.addAll(afterByCompetency.keySet());

        return competencyIds.stream()
                .map(id -> toDelta(beforeByCompetency.get(id), afterByCompetency.get(id)))
                .sorted(Comparator.comparing(CompetencyDelta::displayOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CompetencyDelta::competencyId))
                .toList();
    }

    private static Map<Integer, CompetencyResult> indexByCompetency(List<CompetencyResult> scores) {
        return scores.stream().collect(Collectors.toMap(
                CompetencyResult::competencyId, Function.identity(), (l, r) -> l, LinkedHashMap::new));
    }

    private static CompetencyDelta toDelta(CompetencyResult before, CompetencyResult after) {
        CompetencyResult present = before != null ? before : after;
        BigDecimal beforeScore = before != null ? before.convertedScore() : null;
        BigDecimal afterScore = after != null ? after.convertedScore() : null;
        BigDecimal delta = (beforeScore != null && afterScore != null) ? afterScore.subtract(beforeScore) : null;
        return new CompetencyDelta(present.competencyId(), present.competencyName(), present.displayOrder(),
                beforeScore, afterScore, delta);
    }

    private record SideData(AssessmentResultResponse result, AssessmentRound round) {}
}
