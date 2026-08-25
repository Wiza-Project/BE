package com.gnagnoohc.scms.domain.competency.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;
import static com.gnagnoohc.scms.domain.competency.entity.QAssessmentAttempt.assessmentAttempt;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;

@Repository
@RequiredArgsConstructor
public class AssessmentAttendanceQueryRepository {

    private static final String STUDENT_USER_TYPE = "STUDENT";

    private final JPAQueryFactory queryFactory;
    private final TargetConditionInterpreter targetConditionInterpreter;

    public long countTargetStudents(JsonNode targetCondition) {
        BooleanExpression conditionPredicate = targetConditionInterpreter.toPredicate(targetCondition);

        Long count = queryFactory
                .select(appUser.count())
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(appUser.userType.eq(STUDENT_USER_TYPE), conditionPredicate)
                .fetchOne();
        return count != null ? count : 0L;
    }

    // 분자를 "회차 전체 제출 건수"가 아니라 "대상 조건에 맞는 학생의 제출 건수"로 좁힌다. 진단 안내
    // 화면이 아직 target_condition으로 동의를 막지 않아 대상 조건 밖 학생도 attempt를 만들 수 있는데,
    // 그걸 그대로 분자에 더하면 응시율이 100%를 넘어가는 값이 나올 수 있다.
    public long countCompletedAttempts(Integer assessmentRoundId, JsonNode targetCondition) {
        BooleanExpression conditionPredicate = targetConditionInterpreter.toPredicate(targetCondition);

        Long count = queryFactory
                .select(assessmentAttempt.count())
                .from(assessmentAttempt)
                .join(assessmentAttempt.student, appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(
                        assessmentAttempt.assessmentRound.assessmentRoundId.eq(assessmentRoundId),
                        assessmentAttempt.submittedAt.isNotNull(),
                        conditionPredicate
                )
                .fetchOne();
        return count != null ? count : 0L;
    }
}
