package com.gnagnoohc.scms.domain.competency.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;

// 학생 한 명이 특정 회차의 대상 조건(target_condition)에 해당하는지 판정한다.
// 응시율·미응시자 조회와 같은 해석기(TargetConditionInterpreter)를 써서 세 곳의 대상자 판정이 어긋나지 않게 한다.
@Repository
@RequiredArgsConstructor
public class StudentAssessmentRoundQueryRepository {

    private static final String STUDENT_USER_TYPE = "STUDENT";

    private final JPAQueryFactory queryFactory;
    private final TargetConditionInterpreter targetConditionInterpreter;

    public boolean isStudentTargeted(Integer studentId, JsonNode targetCondition) {
        BooleanExpression conditionPredicate = targetConditionInterpreter.toPredicate(targetCondition);
        if (conditionPredicate == null) {
            return true; // target_condition이 없으면 전체 학생 대상
        }

        Integer matched = queryFactory
                .select(appUser.userId)
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(
                        appUser.userId.eq(studentId),
                        appUser.userType.eq(STUDENT_USER_TYPE),
                        conditionPredicate
                )
                .fetchFirst();
        return matched != null;
    }
}
