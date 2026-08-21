package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.StudentJobRelation;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.gnagnoohc.scms.domain.career.entity.QCompanyAccount.companyAccount;
import static com.gnagnoohc.scms.domain.career.entity.QJobPosting.jobPosting;
import static com.gnagnoohc.scms.domain.career.entity.QStudentJobRelation.studentJobRelation;
import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;

/**
 * StudentJobRelation QueryDSL 동적 쿼리 및 성능 최적화 구현체
 *
 * <p><strong>[가이드라인 및 아키텍처 원칙]</strong></p>
 * <ul>
 *   <li><b>Fetch Join 최적화:</b> {@code open-in-view: false} 환경에서 DTO 변환 시 발생하는 지연 로딩(LAZY) N+1 문제를 방지하기 위해 연관 엔티티 일괄 패치 조인 적용</li>
 *   <li><b>카운트 쿼리 최적화:</b> {@link PageableExecutionUtils}를 활용하여 첫 페이지 데이터 수가 페이지 크기보다 작거나 마지막 페이지인 경우 불필요한 COUNT 쿼리 실행 생략</li>
 *   <li><b>동적 검색 조건:</b> 전형 상태({@code application_status}) 등의 선택적 조건을 {@link BooleanExpression}으로 안전하게 바인딩</li>
 * </ul>
 *
 * @author YUN
 */
@Repository
@RequiredArgsConstructor
public class StudentJobRelationRepositoryImpl implements StudentJobRelationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 학생 관심 공고 스크랩 목록 페이징 조회
     *
     * <p><strong>[비즈니스 및 쿼리 전략]</strong></p>
     * <ul>
     *   <li><b>조회 조건:</b> 로그인 학생 본인 식별자({@code student.userId}) 및 북마크 일시 존재({@code bookmarkedAt IS NOT NULL})</li>
     *   <li><b>정렬 기준:</b> 채용공고 마감 임박순({@code jobPosting.applicationEndsAt ASC})</li>
     *   <li><b>조인 전략:</b> 카드 UI 렌더링에 필요한 {@code JobPosting}, {@code CompanyAccount} Fetch Join</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param pageable      페이징 파라미터
     * @return 스크랩 관계 엔티티 페이징 객체
     */
    @Override
    public Page<StudentJobRelation> findScrappedPostingsByStudent(Integer studentUserId, Pageable pageable) {
        List<StudentJobRelation> content = queryFactory
                .selectFrom(studentJobRelation)
                .join(studentJobRelation.jobPosting, jobPosting).fetchJoin()
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .where(
                        studentJobRelation.student.userId.eq(studentUserId),
                        studentJobRelation.bookmarkedAt.isNotNull()
                )
                .orderBy(jobPosting.applicationEndsAt.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(studentJobRelation.count())
                .from(studentJobRelation)
                .where(
                        studentJobRelation.student.userId.eq(studentUserId),
                        studentJobRelation.bookmarkedAt.isNotNull()
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 학생 내 지원 내역 및 전형 상태 목록 페이징 조회
     *
     * <p><strong>[비즈니스 및 쿼리 전략]</strong></p>
     * <ul>
     *   <li><b>조회 조건:</b> 로그인 학생 본인 식별자({@code student.userId}) 및 지원 완료 건({@code appliedAt IS NOT NULL})</li>
     *   <li><b>정렬 기준:</b> 최신 지원일시 내림차순({@code studentJobRelation.appliedAt DESC})</li>
     *   <li><b>조인 전략:</b> 지원 내역 상세 표시에 필요한 {@code JobPosting}, {@code CompanyAccount} Fetch Join</li>
     * </ul>
     *
     * @param studentUserId 학생 식별자 (app_user.user_id)
     * @param pageable      페이징 파라미터
     * @return 지원 이력 관계 엔티티 페이징 객체
     */
    @Override
    public Page<StudentJobRelation> findApplicationsByStudent(Integer studentUserId, Pageable pageable) {
        List<StudentJobRelation> content = queryFactory
                .selectFrom(studentJobRelation)
                .join(studentJobRelation.jobPosting, jobPosting).fetchJoin()
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .where(
                        studentJobRelation.student.userId.eq(studentUserId),
                        studentJobRelation.appliedAt.isNotNull()
                )
                .orderBy(studentJobRelation.appliedAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(studentJobRelation.count())
                .from(studentJobRelation)
                .where(
                        studentJobRelation.student.userId.eq(studentUserId),
                        studentJobRelation.appliedAt.isNotNull()
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 교직원 공고별 지원자 목록 및 전형 관리 페이징 조회
     *
     * <p><strong>[비즈니스 및 쿼리 전략]</strong></p>
     * <ul>
     *   <li><b>조회 조건:</b> 특정 채용공고({@code jobPostingId}), 지원 완료({@code appliedAt IS NOT NULL}), 전형 상태({@code applicationStatus}) 동적 필터링</li>
     *   <li><b>정렬 기준:</b> 접수일시 오름차순({@code studentJobRelation.appliedAt ASC}, 선착순)</li>
     *   <li><b>조인 전략:</b> 지원자 식별({@code AppUser}), 공고 정보({@code JobPosting}), 기업 정보({@code CompanyAccount}) 3단 Fetch Join을 통해 응답 DTO 조립 시 N+1 원천 차단</li>
     * </ul>
     *
     * @param jobPostingId      채용공고 식별자
     * @param applicationStatus 전형 상태 필터 (APPLIED, DOCUMENT_PASS 등, null 허용)
     * @param pageable          페이징 파라미터
     * @return 지원자 관계 엔티티 페이징 객체
     */
    @Override
    public Page<StudentJobRelation> findApplicantsByJobPosting(Integer jobPostingId, String applicationStatus, Pageable pageable) {
        List<StudentJobRelation> content = queryFactory
                .selectFrom(studentJobRelation)
                .join(studentJobRelation.student, appUser).fetchJoin()
                .join(studentJobRelation.jobPosting, jobPosting).fetchJoin()
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .where(
                        studentJobRelation.jobPosting.jobPostingId.eq(jobPostingId),
                        studentJobRelation.appliedAt.isNotNull(),
                        eqApplicationStatus(applicationStatus)
                )
                .orderBy(studentJobRelation.appliedAt.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(studentJobRelation.count())
                .from(studentJobRelation)
                .where(
                        studentJobRelation.jobPosting.jobPostingId.eq(jobPostingId),
                        studentJobRelation.appliedAt.isNotNull(),
                        eqApplicationStatus(applicationStatus)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 학생-공고 관계 단건 상세 Fetch Join 조회
     *
     * <p><strong>[비즈니스 및 쿼리 전략]</strong></p>
     * <ul>
     *   <li><b>조회 조건:</b> 관계 식별자 PK({@code relationId})</li>
     *   <li><b>조인 전략:</b> {@code JobPosting}, {@code CompanyAccount}, {@code AppUser} 일괄 즉시 로딩</li>
     * </ul>
     *
     * @param relationId 관계 식별자 (student_job_relation_id)
     * @return 연관 엔티티가 모두 패치된 StudentJobRelation Optional
     */
    @Override
    public Optional<StudentJobRelation> findByIdWithDetails(Integer relationId) {
        StudentJobRelation result = queryFactory
                .selectFrom(studentJobRelation)
                .join(studentJobRelation.jobPosting, jobPosting).fetchJoin()
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .join(studentJobRelation.student, appUser).fetchJoin()
                .where(studentJobRelation.studentJobRelationId.eq(relationId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 전형 상태 동적 검색 조건 생성
     *
     * <p><strong>[조건 처리 규칙]</strong></p>
     * <ul>
     *   <li>{@code applicationStatus} 값이 존재할 경우: {@code studentJobRelation.applicationStatus.eq(...)} 적용</li>
     *   <li>{@code applicationStatus} 값이 null 또는 공백일 경우: {@code null} 반환 (WHERE 절 조건 제외)</li>
     * </ul>
     *
     * @param applicationStatus 전형 상태 문자열
     * @return QueryDSL BooleanExpression 조건식
     */
    private BooleanExpression eqApplicationStatus(String applicationStatus) {
        if (!StringUtils.hasText(applicationStatus)) {
            return null;
        }
        return studentJobRelation.applicationStatus.eq(applicationStatus);
    }
}