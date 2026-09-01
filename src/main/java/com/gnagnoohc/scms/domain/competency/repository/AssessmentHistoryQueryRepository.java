package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentHistoryResponse;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.gnagnoohc.scms.domain.competency.entity.QAssessmentAttempt.assessmentAttempt;

// 과거 진단 결과 목록 전용. 목록만 담당하고 상세 점수는 결과 조회 API를
// 재사용하므로(AssessmentResultService), 여기서는 assessment_score를 조인하지 않는다.
@Repository
@RequiredArgsConstructor
public class AssessmentHistoryQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 응시완료 = submittedAt IS NOT NULL(제출 트랜잭션에서 환산점수까지 같이 저장되므로 이 시점 이후엔
     * 항상 채점도 끝난 상태다 — AssessmentNonParticipantQueryRepository의 "제출 완료" 판정 기준과 동일).
     * 일자순 정렬은 최신 응시가 먼저 보이도록 submittedAt 내림차순으로 고정한다.
     */
    public Page<AssessmentHistoryResponse> findHistory(Integer studentId, String keyword, Pageable pageable) {
        BooleanBuilder condition = new BooleanBuilder()
                .and(assessmentAttempt.student.userId.eq(studentId))
                .and(assessmentAttempt.submittedAt.isNotNull());
        if (StringUtils.hasText(keyword)) {
            condition.and(assessmentAttempt.assessmentRound.assessmentName.containsIgnoreCase(keyword));
        }

        List<AssessmentHistoryResponse> content = queryFactory
                .select(Projections.constructor(AssessmentHistoryResponse.class,
                        assessmentAttempt.attemptId,
                        assessmentAttempt.assessmentRound.assessmentRoundId,
                        assessmentAttempt.assessmentRound.assessmentName,
                        assessmentAttempt.assessmentRound.academicYear,
                        assessmentAttempt.assessmentRound.semesterCode,
                        assessmentAttempt.assessmentRound.assessmentType,
                        assessmentAttempt.submittedAt))
                .from(assessmentAttempt)
                .where(condition)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                // submittedAt만으로 정렬하면 동시각 제출이 페이지 경계에 걸렸을 때 조회할 때마다
                // 순서가 흔들려 항목이 중복되거나 누락될 수 있어, attemptId를 보조 정렬키로 둔다.
                .orderBy(assessmentAttempt.submittedAt.desc(), assessmentAttempt.attemptId.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(assessmentAttempt.count())
                .from(assessmentAttempt)
                .where(condition);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
