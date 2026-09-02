package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentGroupAxis;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;
import static com.gnagnoohc.scms.domain.competency.entity.QAssessmentAttempt.assessmentAttempt;
import static com.gnagnoohc.scms.domain.competency.entity.QAssessmentScore.assessmentScore;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;

/**
 * 역량별 분포·집단별 비교. assessment_score를 (집단축, competency_id)로 GROUP BY하는
 * 같은 쿼리라 그룹 축을 파라미터로 받는 메서드 하나로 구현한다 — 두 화면을 따로 구현하면
 * 축 순서가 어긋날 위험이 있어서다.
 */
@Repository
@RequiredArgsConstructor
public class AssessmentDistributionQueryRepository {

    private static final String STUDENT_USER_TYPE = "STUDENT";
    // academic_status가 실제로 쓰는 라벨은 재학/휴학/졸업/제적/자퇴 5개. 이 중 재학만 대상자로 본다.
    private static final String ENROLLED_ACADEMIC_STATUS = "재학";

    // 대상자 = STUDENT 중 학적상태 '재학'. 응시율·미응시자 명단과 같은 모수를 쓴다 —
    // 졸업·제적·자퇴·휴학 학생의 점수는 집단 평균에 섞이지 않는다.
    private static final BooleanExpression ENROLLED_STUDENT =
            appUser.userType.eq(STUDENT_USER_TYPE).and(appUser.academicStatus.eq(ENROLLED_ACADEMIC_STATUS));

    private final JPAQueryFactory queryFactory;

    // 학년/학과별 그룹핑 표현식만 갈아끼우고 나머지 쿼리(조인·WHERE·집계)는 공유한다.
    public List<GroupCompetencyAggregate> aggregateByGroupAxis(Integer assessmentRoundId, AssessmentGroupAxis groupAxis) {
        StringExpression groupKey;
        StringExpression groupLabel;
        if (groupAxis == AssessmentGroupAxis.GRADE) {
            groupKey = studentAcademicDetail.grade.stringValue();
            groupLabel = studentAcademicDetail.grade.stringValue().concat("학년");
        } else {
            groupKey = studentAcademicDetail.majorCode.codeId.stringValue();
            groupLabel = studentAcademicDetail.majorCode.codeName;
        }

        return queryFactory
                .select(Projections.constructor(GroupCompetencyAggregate.class,
                        groupKey,
                        groupLabel,
                        assessmentScore.competency.competencyId,
                        assessmentScore.competency.competencyName,
                        assessmentScore.competency.displayOrder,
                        assessmentScore.convertedScore.avg(),
                        assessmentScore.count()))
                .from(assessmentScore)
                .join(assessmentScore.attempt, assessmentAttempt)
                .join(assessmentAttempt.student, appUser)
                // 학적 상세가 없는 학생은 학년·학과로 묶을 수 없어 INNER JOIN으로 자동 제외한다(집계 목적상 의도된 동작).
                .join(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(
                        assessmentAttempt.assessmentRound.assessmentRoundId.eq(assessmentRoundId),
                        ENROLLED_STUDENT
                )
                .groupBy(groupKey, groupLabel,
                        assessmentScore.competency.competencyId,
                        assessmentScore.competency.competencyName,
                        assessmentScore.competency.displayOrder)
                .orderBy(groupKey.asc(), assessmentScore.competency.displayOrder.asc())
                .fetch();
    }

    /**
     * assessment_score는 제출 트랜잭션에서 attempt당 역량별로 한 행씩만 생성되므로(AssessmentSubmissionService),
     * respondentCount(=이 (그룹,역량) 조합의 행 수)는 같은 그룹 내 모든 역량에서 항상 동일하다 —
     * 서비스 계층이 그룹당 값을 조립할 때 어느 역량의 값을 가져와도 무방하다.
     *
     * <p>averageScore/respondentCount 타입은 QueryDSL 표현식이 내는 그대로 둔다 — avg()는 Double,
     * count()는 Long이다. BigDecimal이나 primitive long으로 바꾸면 Projections.constructor가
     * 이 시그니처에 맞는 생성자를 못 찾아 쿼리 빌드 단계에서 ExpressionException이 난다.
     * 화면 표시용 BigDecimal 반올림은 서비스가 맡는다.
     */
    public record GroupCompetencyAggregate(
            String groupKey,
            String groupLabel,
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            Double averageScore,
            Long respondentCount
    ) {}
}
