package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.support.AssessmentTargetPolicy;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter.StudentTargetSnapshot;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;

// 학생 응시 가능 회차 목록의 대상 조건 판정용. 회차마다 쿼리를 날리지 않도록,
// 판정에 필요한 학적(재학 여부·학년·전공)만 한 번 읽어 스냅샷으로 넘긴다. 실제 판정은 TargetConditionInterpreter.matches()가 한다.
@Repository
@RequiredArgsConstructor
public class StudentAssessmentRoundQueryRepository {

    private final JPAQueryFactory queryFactory;

    public StudentTargetSnapshot loadTargetSnapshot(Integer studentId) {
        Tuple row = queryFactory
                .select(studentAcademicDetail.grade, studentAcademicDetail.majorCode.codeId)
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(appUser.userId.eq(studentId), AssessmentTargetPolicy.ENROLLED_STUDENT)
                .fetchOne();

        // 대상자 조건을 학적 상세 조인이 아니라 app_user 쪽 WHERE에 걸었으므로, 재학생이 아니면
        // (또는 STUDENT 계정이 아니면) 행 자체가 안 잡힌다 — 학적 상세 유무와는 무관하게 여기서 걸린다.
        if (row == null) {
            return StudentTargetSnapshot.notEnrolled();
        }
        Short grade = row.get(studentAcademicDetail.grade);
        Integer majorCodeId = row.get(studentAcademicDetail.majorCode.codeId);
        if (grade == null && majorCodeId == null) {
            return StudentTargetSnapshot.enrolledWithoutAcademicDetail();
        }
        return new StudentTargetSnapshot(true, true, grade == null ? null : grade.intValue(), majorCodeId);
    }
}
