package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.gnagnoohc.scms.domain.competency.entity.QAssessmentAttempt.assessmentAttempt;
import static com.gnagnoohc.scms.domain.competency.entity.QAssessmentScore.assessmentScore;
import static com.gnagnoohc.scms.domain.competency.entity.QCompetency.competency;
import static com.gnagnoohc.scms.domain.competency.support.AssessmentTargetPolicy.ENROLLED_STUDENT;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;

/**
 * 백분위 산출 배치 전용 assessment_score 조회. Spring Data(AssessmentScoreRepository)의 단순
 * @Query로는 재학생 조건을 넣으려면 '재학'·'STUDENT' 리터럴을 또 박아야 해서, 판정 기준을 한 곳
 * (AssessmentTargetPolicy)에서만 쓰도록 QueryDSL로 뺐다.
 */
@Repository
@RequiredArgsConstructor
public class AssessmentScoreQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 회차 하나의 assessment_score를 재학생 것만 모아 competency까지 fetch join해 돌려준다.
     *
     * <p>비재학생(휴학·졸업·제적·자퇴) 점수를 백분위 분모에서 빼야 응시율·집단 평균과 같은 모수가 된다
     * (AssessmentTargetPolicy). 응시 차단 이전에 만들어진 비재학생 제출 행이 회차에 섞여 있어도 여기서
     * 걸러지므로, 이 배치가 도는 회차의 개인 백분위는 재학생 기준으로 매겨진다.
     *
     * <p>competency fetch join은 호출부(AssessmentPercentileCalculator)가 점수마다
     * competency.competencyId를 읽어 N+1이 나는 걸 막는다.
     */
    public List<AssessmentScore> findEnrolledScoresByRoundIdFetchCompetency(Integer roundId) {
        return queryFactory
                .selectFrom(assessmentScore)
                .join(assessmentScore.competency, competency).fetchJoin()
                .join(assessmentScore.attempt, assessmentAttempt)
                .join(assessmentAttempt.student, appUser)
                .where(
                        assessmentAttempt.assessmentRound.assessmentRoundId.eq(roundId),
                        ENROLLED_STUDENT
                )
                .fetch();
    }
}
