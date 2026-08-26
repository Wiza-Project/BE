package com.gnagnoohc.scms.domain.competency.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentNonParticipantResponse;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;
import static com.gnagnoohc.scms.domain.competency.entity.QAssessmentAttempt.assessmentAttempt;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;
import static com.gnagnoohc.scms.global.common.entity.QCommonCode.commonCode;

/**
 * 미응시자 목록 전용 QueryDSL 레포지토리. AssessmentAttendanceQueryRepository와 대상자
 * 판정 기준(target_condition 해석·완료 기준)은 같지만, 개인정보가 담긴 행을 그대로
 * 반환하므로 응시율 집계와는 별도 클래스로 분리한다.
 */
@Repository
@RequiredArgsConstructor
public class AssessmentNonParticipantQueryRepository {

    private static final String STUDENT_USER_TYPE = "STUDENT";

    private final JPAQueryFactory queryFactory;
    private final TargetConditionInterpreter targetConditionInterpreter;

    // 미응시 = 대상 조건에 맞으면서, 이 회차에 제출 완료(submittedAt IS NOT NULL) 처리된
    // attempt가 없는 학생. 중도저장만 하고 제출하지 않은 학생도 미응시로 잡는다
    // (AssessmentAttendanceQueryRepository.countCompletedAttempts와 동일한 완료 기준).
    public Page<AssessmentNonParticipantResponse> findNonParticipants(Integer assessmentRoundId, JsonNode targetCondition, Pageable pageable) {
        BooleanExpression conditionPredicate = targetConditionInterpreter.toPredicate(targetCondition);
        BooleanExpression notSubmitted = appUser.userId.notIn(submittedStudentIds(assessmentRoundId));

        List<AssessmentNonParticipantResponse> content = queryFactory
                .select(Projections.constructor(AssessmentNonParticipantResponse.class,
                        appUser.userId,
                        appUser.universityNo,
                        appUser.userName,
                        appUser.email,
                        appUser.phone,
                        studentAcademicDetail.majorCode.codeName,
                        studentAcademicDetail.grade.intValue()))
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .leftJoin(studentAcademicDetail.majorCode, commonCode)
                .where(appUser.userType.eq(STUDENT_USER_TYPE), conditionPredicate, notSubmitted)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(appUser.universityNo.asc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(appUser.count())
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(appUser.userType.eq(STUDENT_USER_TYPE), conditionPredicate, notSubmitted);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 알림 발송(AssessmentNonParticipantService.notify) 대상 확정용. 페이지가 아니라 전체
    // userId 집합이 필요하므로 findNonParticipants와 같은 조건을 재사용하되 페이징은 하지 않는다.
    public List<Integer> findNonParticipantUserIds(Integer assessmentRoundId, JsonNode targetCondition) {
        BooleanExpression conditionPredicate = targetConditionInterpreter.toPredicate(targetCondition);
        BooleanExpression notSubmitted = appUser.userId.notIn(submittedStudentIds(assessmentRoundId));

        return queryFactory
                .select(appUser.userId)
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(appUser.userType.eq(STUDENT_USER_TYPE), conditionPredicate, notSubmitted)
                .fetch();
    }

    private JPQLQuery<Integer> submittedStudentIds(Integer assessmentRoundId) {
        return JPAExpressions
                .select(assessmentAttempt.student.userId)
                .from(assessmentAttempt)
                .where(
                        assessmentAttempt.assessmentRound.assessmentRoundId.eq(assessmentRoundId),
                        assessmentAttempt.submittedAt.isNotNull()
                );
    }
}
