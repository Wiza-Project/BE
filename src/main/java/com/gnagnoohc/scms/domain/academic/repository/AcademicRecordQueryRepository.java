package com.gnagnoohc.scms.domain.academic.repository;

import com.gnagnoohc.scms.domain.academic.dto.AdminStudentListItemResponse;
import com.gnagnoohc.scms.domain.academic.dto.AdminStudentSearchConditionDTO;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicChange.studentAcademicChange;
import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;
import static com.gnagnoohc.scms.global.common.entity.QCommonCode.commonCode;

/**
 * 학적조회 목록/통계 전용 QueryDSL 레포지토리.
 *
 * <p>{@code app_user}가 학생 존재의 원본이고 {@code student_academic_detail}은 없을 수도
 * 있는 종속 행이라(설계 문서 3-1장), 목록은 {@code app_user}를 FROM 절 시작점으로 삼고
 * {@code student_academic_detail}·{@code common_code}(MAJOR)는 전부 LEFT JOIN한다 —
 * INNER JOIN으로 하면 학적 상세를 아직 입력 안 한 학생이 목록에서 통째로 빠진다.</p>
 *
 * <p>입학일자 같은 파생값은 목록 쿼리 자체에 넣지 않는다. 대신 페이지에 실린
 * {@code userId}들을 모아 {@link #findAdmissionDates} 한 번으로 in절 조회해서 Java에서
 * 합친다 — 행마다 따로 조회하면 N+1이 터진다(주의사항 7).</p>
 */
@Repository
@RequiredArgsConstructor
public class AcademicRecordQueryRepository {

    /** app_user.academic_status가 실제로 쓰는 5개 라벨(설계 문서 5-1장, 1차 회신 3장 확정). */
    private static final List<String> KNOWN_STATUSES = List.of("재학", "휴학", "졸업", "제적", "자퇴");
    private static final String ADMISSION_TYPE_CODE = "AC100";

    private final JPAQueryFactory queryFactory;

    public Page<AdminStudentListItemResponse> searchStudents(AdminStudentSearchConditionDTO cond, Pageable pageable) {
        List<AdminStudentListItemResponse> content = queryFactory
                .select(Projections.constructor(AdminStudentListItemResponse.class,
                        appUser.userId,
                        appUser.universityNo,
                        appUser.userName,
                        appUser.phone,
                        appUser.email,
                        appUser.academicStatus,
                        studentAcademicDetail.majorCode.code,
                        studentAcademicDetail.majorCode.codeName,
                        studentAcademicDetail.grade.intValue(),
                        Expressions.nullExpression(LocalDate.class)))
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .leftJoin(studentAcademicDetail.majorCode, commonCode)
                .where(
                        appUser.userType.eq("STUDENT"),
                        majorCodeIdEq(cond.getMajorCodeId()),
                        gradeEq(cond.getGrade()),
                        statusEq(cond.getStatus()),
                        keywordContains(cond.getKeyword())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(appUser.universityNo.asc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(appUser.count())
                .from(appUser)
                .leftJoin(studentAcademicDetail).on(studentAcademicDetail.userId.eq(appUser.userId))
                .where(
                        appUser.userType.eq("STUDENT"),
                        majorCodeIdEq(cond.getMajorCodeId()),
                        gradeEq(cond.getGrade()),
                        statusEq(cond.getStatus()),
                        keywordContains(cond.getKeyword())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /** 페이지에 실린 학생들의 입학일자(AC100 최초 행)를 in절 한 번으로 일괄 조회한다. */
    public Map<Integer, LocalDate> findAdmissionDates(List<Integer> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        List<Tuple> rows = queryFactory
                .select(studentAcademicChange.student.userId, studentAcademicChange.changeDate.min())
                .from(studentAcademicChange)
                .where(
                        studentAcademicChange.student.userId.in(studentIds),
                        studentAcademicChange.changeTypeCode.code.eq(ADMISSION_TYPE_CODE)
                )
                .groupBy(studentAcademicChange.student.userId)
                .fetch();

        Map<Integer, LocalDate> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            result.put(row.get(studentAcademicChange.student.userId), row.get(studentAcademicChange.changeDate.min()));
        }
        return result;
    }

    public long countTotalStudents() {
        Long count = queryFactory.select(appUser.count())
                .from(appUser)
                .where(appUser.userType.eq("STUDENT"))
                .fetchOne();
        return count != null ? count : 0L;
    }

    /** 상태별 학생 수. 0건인 상태도 0으로 채워 반환한다(FE가 매 상태 타일을 항상 그릴 수 있게). */
    public Map<String, Long> countByStatus() {
        List<Tuple> rows = queryFactory
                .select(appUser.academicStatus, appUser.count())
                .from(appUser)
                .where(appUser.userType.eq("STUDENT"))
                .groupBy(appUser.academicStatus)
                .fetch();

        Map<String, Long> result = new LinkedHashMap<>();
        KNOWN_STATUSES.forEach(status -> result.put(status, 0L));
        for (Tuple row : rows) {
            String status = row.get(appUser.academicStatus);
            if (status != null) {
                result.put(status, row.get(appUser.count()));
            }
        }
        return result;
    }

    private BooleanExpression majorCodeIdEq(Integer majorCodeId) {
        return majorCodeId != null ? studentAcademicDetail.majorCode.codeId.eq(majorCodeId) : null;
    }

    private BooleanExpression gradeEq(Integer grade) {
        return grade != null ? studentAcademicDetail.grade.eq(grade.shortValue()) : null;
    }

    private BooleanExpression statusEq(String status) {
        return StringUtils.hasText(status) ? appUser.academicStatus.eq(status) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.hasText(keyword)
                ? appUser.userName.containsIgnoreCase(keyword).or(appUser.universityNo.containsIgnoreCase(keyword))
                : null;
    }
}
