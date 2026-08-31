package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionRow;
import com.gnagnoohc.scms.domain.counsel.entity.QCounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.QCounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.QCounselingSession;
import com.gnagnoohc.scms.domain.counsel.entity.QCounselingType;
import com.gnagnoohc.scms.domain.user.entity.QAppUser;
import com.gnagnoohc.scms.global.common.entity.QCommonCode;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.time.Instant;
import java.util.List;

/**
 * 회기 목록 동적 필터를 QueryDSL로 구현한다. 선택 필터가 null이면 조건 메서드가 null을 반환해
 * where 절에서 자동으로 빠지므로, 타입 없는 NULL 바인드가 SQL에 아예 나가지 않는다.
 * content 쿼리와 count 쿼리는 같은 조건 메서드를 공유해 필터 의미가 항상 일치한다.
 */
@RequiredArgsConstructor
public class CounselingSessionRepositoryImpl implements CounselingSessionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 같은 app_user 테이블을 학생·상담사 두 역할로 각각 조인하므로 별칭을 분리한다.
    private static final QCounselingSession session = QCounselingSession.counselingSession;
    private static final QCounselingAssignment assignment = QCounselingAssignment.counselingAssignment;
    private static final QCounselingReservation reservation = QCounselingReservation.counselingReservation;
    private static final QCounselingType counselingType = QCounselingType.counselingType;
    private static final QAppUser student = new QAppUser("student");
    private static final QAppUser counselor = new QAppUser("counselor");
    private static final QCommonCode department = new QCommonCode("department");

    @Override
    public Page<CounselingSessionRow> findSessions(
            Integer counselorId, String sessionStatus, Instant from, Instant to, Pageable pageable
    ) {
        List<CounselingSessionRow> content = queryFactory
                .select(Projections.constructor(
                        CounselingSessionRow.class,
                        session.counselingSessionId, assignment.counselingAssignmentId,
                        reservation.counselingReservationId, session.sessionNo,
                        student.userId, student.universityNo, student.userName, department.codeName,
                        counselingType.typeName, session.startsAt, session.endsAt,
                        session.attendanceStatus, session.sessionStatus,
                        session.nextSessionAt, session.cancellationReason,
                        assignment.assignedAt, assignment.endedAt, counselor.userId
                ))
                .from(session)
                .join(session.counselingAssignment, assignment)
                .join(assignment.counselingReservation, reservation)
                .join(reservation.student, student)
                .leftJoin(student.departmentCode, department)
                .join(reservation.counselingType, counselingType)
                .join(assignment.counselor, counselor)
                .where(
                        counselor.userId.eq(counselorId),
                        sessionStatusEq(sessionStatus),
                        startsAtGoe(from),
                        startsAtLt(to)
                )
                .orderBy(session.startsAt.desc(), session.counselingSessionId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // count 쿼리도 content와 동일한 조인·조건을 써서 totalElements 의미를 정확히 맞춘다.
        JPAQuery<Long> countQuery = queryFactory
                .select(session.count())
                .from(session)
                .join(session.counselingAssignment, assignment)
                .join(assignment.counselingReservation, reservation)
                .join(reservation.student, student)
                .leftJoin(student.departmentCode, department)
                .join(reservation.counselingType, counselingType)
                .join(assignment.counselor, counselor)
                .where(
                        counselor.userId.eq(counselorId),
                        sessionStatusEq(sessionStatus),
                        startsAtGoe(from),
                        startsAtLt(to)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // --- 동적 조건 메서드: 값이 null이면 null을 반환해 where에서 제외된다 ---

    private BooleanExpression sessionStatusEq(String sessionStatus) {
        return sessionStatus != null ? session.sessionStatus.eq(sessionStatus) : null;
    }

    private BooleanExpression startsAtGoe(Instant from) {
        return from != null ? session.startsAt.goe(from) : null;
    }

    private BooleanExpression startsAtLt(Instant to) {
        return to != null ? session.startsAt.lt(to) : null;
    }
}
