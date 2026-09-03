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
    // academic_status가 실제로 쓰는 라벨은 재학/휴학/졸업/제적/자퇴 5개. 이 중 재학만 대상자로 본다.
    private static final String ENROLLED_ACADEMIC_STATUS = "재학";

    // 대상자 = STUDENT 중 학적상태 '재학'. 졸업·제적·자퇴·휴학은 응시율 분모(대상자 수)에도
    // 분자(완료 건수)에도 들어가지 않는다 — 미응시자 명단·결과 통계와 같은 모수.
    private static final BooleanExpression ENROLLED_STUDENT =
            appUser.userType.eq(STUDENT_USER_TYPE).and(appUser.academicStatus.eq(ENROLLED_ACADEMIC_STATUS));

    private final JPAQueryFactory queryFactory;
    private final TargetConditionInterpreter targetConditionInterpreter;

    public long countTargetStudents(JsonNode targetCondition) {
        BooleanExpression conditionPredicate = targetConditionInterpreter.toPredicate(targetCondition);

        Long count = queryFactory
                .select(appUser.count())
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(ENROLLED_STUDENT, conditionPredicate)
                .fetchOne();
        return count != null ? count : 0L;
    }

    /**
     * 분자를 "회차 전체 제출 건수"가 아니라 "대상 조건에 맞는 학생의 제출 건수"로 좁힌다. 진단 안내
     * 화면이 아직 target_condition으로 동의를 막지 않아 대상 조건 밖 학생도 attempt를 만들 수 있는데,
     * 그걸 그대로 분자에 더하면 응시율이 100%를 넘어가는 값이 나올 수 있다.
     * countTargetStudents와 동일하게 재학 학생으로 제한한다(ENROLLED_STUDENT) — student FK에 타입
     * 제약이 없어, 이 조건이 없으면 분모(재학생만)와 분자(전체 유저 타입)가 다른 모집단을 비교하게 된다.
     */
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
                        ENROLLED_STUDENT,
                        conditionPredicate
                )
                .fetchOne();
        return count != null ? count : 0L;
    }
}
