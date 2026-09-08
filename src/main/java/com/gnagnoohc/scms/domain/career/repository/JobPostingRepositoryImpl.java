package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.dto.posting.JobPostingSearchConditionDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPosting;
import com.gnagnoohc.scms.global.common.entity.QCommonCode;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

import static com.gnagnoohc.scms.domain.career.entity.QCompanyAccount.companyAccount;
import static com.gnagnoohc.scms.domain.career.entity.QJobPosting.jobPosting;
import static com.gnagnoohc.scms.global.common.entity.QCommonCode.commonCode;

/**
 * 채용공고 QueryDSL 동적 쿼리 커스텀 레포지토리 구현체
 *
 * <p><strong>[핵심 설계 기준]</strong></p>
 * <ul>
 *   <li><b>N+1 방지:</b> {@code companyAccount}, {@code ncsCode}, {@code regionCode} 패치 조인(Fetch Join) 적용</li>
 *   <li><b>사용자 역할별 분기:</b>
 *     <ul>
 *       <li>{@link #searchStudentPostings}: 학생용 (게시 완료 및 마감일 미경과 공고만 노출, 마감 임박순 정렬)</li>
 *       <li>{@link #searchStaffPostings}: 교직원용 (전체 상태 공고 검수 및 모니터링, 최신 등록순 정렬)</li>
 *     </ul>
 *   </li>
 *   <li><b>동적 검색:</b> {@code BooleanExpression}을 통해 유효한 파라미터만 WHERE 절에 바인딩 (null 자동 무시)</li>
 *   <li><b>카운트 최적화:</b> {@code PageableExecutionUtils}를 활용한 불필요한 COUNT 쿼리 생략</li>
 * </ul>
 *
 * @author YUN
 */
@RequiredArgsConstructor
public class JobPostingRepositoryImpl implements JobPostingRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QCommonCode ncsCommonCode = new QCommonCode("ncsCommonCode");
    private final QCommonCode regionCommonCode = new QCommonCode("regionCommonCode");

    @Override
    public Page<JobPosting> searchStudentPostings(JobPostingSearchConditionDTO cond, Pageable pageable) {
        List<JobPosting> content = queryFactory
                .selectFrom(jobPosting)
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .leftJoin(jobPosting.ncsCode, ncsCommonCode).fetchJoin()         // 공통코드 받는 - 엔티티에서 - ncsCode 필드 참조
                .leftJoin(jobPosting.regionCode, regionCommonCode).fetchJoin()   // 공통코드 받는 - 엔티티에서 - regionCode 필드 참조
                .where(
                        jobPosting.postingStatus.eq("PUBLISHED"),
                        jobPosting.applicationEndsAt.goe(Instant.now()),
                        ncsCodeIdEq(cond.getNcsCodeId()),
                        regionCodeIdEq(cond.getRegionCodeId()),
                        companyNameContains(cond.getCompanyName()),
                        employmentTypeEq(cond.getEmploymentType()),
                        postingTypeEq(cond.getPostingType())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(jobPosting.applicationEndsAt.asc(), jobPosting.jobPostingId.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(jobPosting.count())
                .from(jobPosting)
                .where(
                        jobPosting.postingStatus.eq("PUBLISHED"),
                        jobPosting.applicationEndsAt.goe(Instant.now()),
                        ncsCodeIdEq(cond.getNcsCodeId()),
                        regionCodeIdEq(cond.getRegionCodeId()),
                        companyNameContains(cond.getCompanyName()),
                        employmentTypeEq(cond.getEmploymentType()),
                        postingTypeEq(cond.getPostingType())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<JobPosting> searchStaffPostings(JobPostingSearchConditionDTO cond, Pageable pageable) {
        List<JobPosting> content = queryFactory
                .selectFrom(jobPosting)
                .join(jobPosting.companyAccount, companyAccount).fetchJoin()
                .leftJoin(jobPosting.ncsCode, ncsCommonCode).fetchJoin()         // ncsCode 필드 참조
                .leftJoin(jobPosting.regionCode, regionCommonCode).fetchJoin()   // regionCode 필드 참조
                .where(
                        postingStatusEq(cond.getPostingStatus()),
                        reviewStatusEq(cond.getReviewStatus()),
                        postingTypeEq(cond.getPostingType()),
                        companyNameContains(cond.getCompanyName())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(jobPosting.jobPostingId.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(jobPosting.count())
                .from(jobPosting)
                .where(
                        postingStatusEq(cond.getPostingStatus()),
                        reviewStatusEq(cond.getReviewStatus()),
                        postingTypeEq(cond.getPostingType()),
                        companyNameContains(cond.getCompanyName())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // --- 동적 조건 메서드 ---

    private BooleanExpression ncsCodeIdEq(Integer ncsCodeId) {
        // 엔티티의 ncsCode 객체 내부의 codeId 컬럼을 비교
        return ncsCodeId != null ? jobPosting.ncsCode.codeId.eq(ncsCodeId) : null;
    }

    private BooleanExpression regionCodeIdEq(Integer regionCodeId) {
        // 엔티티의 regionCode 객체 내부의 codeId 컬럼을 비교
        return regionCodeId != null ? jobPosting.regionCode.codeId.eq(regionCodeId) : null;
    }

    private BooleanExpression companyNameContains(String companyName) {
        return StringUtils.hasText(companyName) ? jobPosting.companyAccount.companyName.containsIgnoreCase(companyName) : null;
    }

    private BooleanExpression employmentTypeEq(String employmentType) {
        return StringUtils.hasText(employmentType) ? jobPosting.employmentType.eq(employmentType) : null;
    }

    private BooleanExpression postingTypeEq(String postingType) {
        return StringUtils.hasText(postingType) ? jobPosting.postingType.eq(postingType) : null;
    }

    private BooleanExpression reviewStatusEq(String reviewStatus) {
        return StringUtils.hasText(reviewStatus) ? jobPosting.reviewStatus.eq(reviewStatus) : null;
    }

    private BooleanExpression postingStatusEq(String postingStatus) {
        return StringUtils.hasText(postingStatus) ? jobPosting.postingStatus.eq(postingStatus) : null;
    }
}